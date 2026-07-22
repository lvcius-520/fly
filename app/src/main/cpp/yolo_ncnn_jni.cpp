#include <android/asset_manager_jni.h>
#include <android/bitmap.h>
#include <android/log.h>
#include <jni.h>

#include <algorithm>
#include <cmath>
#include <memory>
#include <string>
#include <vector>

#include "ncnn/mat.h"
#include "ncnn/net.h"

namespace {
constexpr const char* TAG = "YoloNcnn";
constexpr int TARGET_SIZE = 640;
constexpr float NORM_VALS[3] = {1.0f / 255.0f, 1.0f / 255.0f, 1.0f / 255.0f};

struct Object {
    float x0;
    float y0;
    float x1;
    float y1;
    float prob;
    int label;
};

static float intersection_area(const Object& a, const Object& b) {
    const float x0 = std::max(a.x0, b.x0);
    const float y0 = std::max(a.y0, b.y0);
    const float x1 = std::min(a.x1, b.x1);
    const float y1 = std::min(a.y1, b.y1);
    const float w = std::max(0.0f, x1 - x0);
    const float h = std::max(0.0f, y1 - y0);
    return w * h;
}

static void sort_by_score(std::vector<Object>& objects) {
    std::sort(objects.begin(), objects.end(), [](const Object& a, const Object& b) {
        return a.prob > b.prob;
    });
}

static void nms_sorted_bboxes(const std::vector<Object>& objects, std::vector<int>& picked, float nms_threshold) {
    picked.clear();
    const int n = static_cast<int>(objects.size());
    std::vector<float> areas(n);
    for (int i = 0; i < n; i++) {
        areas[i] = std::max(0.0f, objects[i].x1 - objects[i].x0) * std::max(0.0f, objects[i].y1 - objects[i].y0);
    }

    for (int i = 0; i < n; i++) {
        const Object& a = objects[i];
        bool keep = true;
        for (int j : picked) {
            const Object& b = objects[j];
            if (a.label != b.label) continue;

            const float inter_area = intersection_area(a, b);
            const float union_area = areas[i] + areas[j] - inter_area;
            if (union_area > 0.0f && inter_area / union_area > nms_threshold) {
                keep = false;
                break;
            }
        }
        if (keep) picked.push_back(i);
    }
}

static inline float clampf(float value, float low, float high) {
    return std::max(low, std::min(value, high));
}

class YoloDetector {
public:
    bool load(AAssetManager* mgr, const std::string& param_path, const std::string& model_path) {
        net_.clear();
        net_.opt.use_vulkan_compute = false;
        net_.opt.num_threads = 4;

        if (net_.load_param(mgr, param_path.c_str()) != 0) {
            __android_log_print(ANDROID_LOG_ERROR, TAG, "load_param failed: %s", param_path.c_str());
            return false;
        }
        if (net_.load_model(mgr, model_path.c_str()) != 0) {
            __android_log_print(ANDROID_LOG_ERROR, TAG, "load_model failed: %s", model_path.c_str());
            return false;
        }
        return true;
    }

    std::vector<Object> detect(JNIEnv* env, jobject bitmap, float conf_threshold, float iou_threshold) const {
        AndroidBitmapInfo info{};
        if (AndroidBitmap_getInfo(env, bitmap, &info) != ANDROID_BITMAP_RESULT_SUCCESS) {
            __android_log_print(ANDROID_LOG_ERROR, TAG, "AndroidBitmap_getInfo failed");
            return {};
        }
        if (info.format != ANDROID_BITMAP_FORMAT_RGBA_8888) {
            __android_log_print(ANDROID_LOG_ERROR, TAG, "Unsupported bitmap format: %d", info.format);
            return {};
        }

        const int src_w = static_cast<int>(info.width);
        const int src_h = static_cast<int>(info.height);

        int resized_w = src_w;
        int resized_h = src_h;
        float scale = 1.0f;
        if (resized_w > resized_h) {
            scale = static_cast<float>(TARGET_SIZE) / static_cast<float>(resized_w);
            resized_w = TARGET_SIZE;
            resized_h = static_cast<int>(std::round(resized_h * scale));
        } else {
            scale = static_cast<float>(TARGET_SIZE) / static_cast<float>(resized_h);
            resized_h = TARGET_SIZE;
            resized_w = static_cast<int>(std::round(resized_w * scale));
        }

        ncnn::Mat in = ncnn::Mat::from_android_bitmap_resize(env, bitmap, ncnn::Mat::PIXEL_RGB, resized_w, resized_h);

        const int wpad = TARGET_SIZE - resized_w;
        const int hpad = TARGET_SIZE - resized_h;
        const int left = wpad / 2;
        const int right = wpad - left;
        const int top = hpad / 2;
        const int bottom = hpad - top;

        ncnn::Mat in_pad;
        ncnn::copy_make_border(in, in_pad, top, bottom, left, right, ncnn::BORDER_CONSTANT, 114.f);
        in_pad.substract_mean_normalize(nullptr, NORM_VALS);

        ncnn::Extractor ex = net_.create_extractor();
        ex.input("in0", in_pad);

        ncnn::Mat out;
        if (ex.extract("out0", out) != 0) {
            __android_log_print(ANDROID_LOG_ERROR, TAG, "extract out0 failed");
            return {};
        }

        std::vector<Object> proposals = parseOutput(out, conf_threshold, src_w, src_h, scale, left, top);
        if (proposals.empty()) return {};

        sort_by_score(proposals);
        std::vector<int> picked;
        nms_sorted_bboxes(proposals, picked, iou_threshold);

        std::vector<Object> results;
        results.reserve(picked.size());
        for (int i : picked) results.push_back(proposals[i]);
        return results;
    }

private:
    static std::vector<Object> parseOutput(
        const ncnn::Mat& out,
        float conf_threshold,
        int src_w,
        int src_h,
        float scale,
        int pad_left,
        int pad_top
    ) {
        std::vector<Object> objects;
        if (out.dims != 2) {
            __android_log_print(ANDROID_LOG_ERROR, TAG, "Unexpected output dims=%d", out.dims);
            return objects;
        }

        const bool row_major = out.w <= 64 && out.h > out.w;
        const int num_rows = row_major ? out.h : out.w;
        const int num_cols = row_major ? out.w : out.h;
        if (num_cols < 5) {
            __android_log_print(ANDROID_LOG_ERROR, TAG, "Unexpected output shape w=%d h=%d", out.w, out.h);
            return objects;
        }

        for (int i = 0; i < num_rows; i++) {
            float x = 0.f;
            float y = 0.f;
            float w = 0.f;
            float h = 0.f;
            float best_score = -1.f;
            int best_cls = -1;

            if (row_major) {
                const float* row = out.row(i);
                x = row[0];
                y = row[1];
                w = row[2];
                h = row[3];
                for (int c = 4; c < num_cols; c++) {
                    if (row[c] > best_score) {
                        best_score = row[c];
                        best_cls = c - 4;
                    }
                }
            } else {
                const float* row_x = out.row(0);
                const float* row_y = out.row(1);
                const float* row_w = out.row(2);
                const float* row_h = out.row(3);
                x = row_x[i];
                y = row_y[i];
                w = row_w[i];
                h = row_h[i];
                for (int c = 4; c < num_cols; c++) {
                    const float score = out.row(c)[i];
                    if (score > best_score) {
                        best_score = score;
                        best_cls = c - 4;
                    }
                }
            }

            if (best_score < conf_threshold || best_cls < 0) continue;

            float x0 = (x - w * 0.5f - pad_left) / scale;
            float y0 = (y - h * 0.5f - pad_top) / scale;
            float x1 = (x + w * 0.5f - pad_left) / scale;
            float y1 = (y + h * 0.5f - pad_top) / scale;

            x0 = clampf(x0, 0.f, static_cast<float>(src_w - 1));
            y0 = clampf(y0, 0.f, static_cast<float>(src_h - 1));
            x1 = clampf(x1, 0.f, static_cast<float>(src_w - 1));
            y1 = clampf(y1, 0.f, static_cast<float>(src_h - 1));
            if (x1 <= x0 || y1 <= y0) continue;

            objects.push_back({x0, y0, x1, y1, best_score, best_cls});
        }

        return objects;
    }

    ncnn::Net net_;
};

static std::string jstringToStdString(JNIEnv* env, jstring text) {
    if (!text) return {};
    const char* chars = env->GetStringUTFChars(text, nullptr);
    std::string result = chars ? chars : "";
    if (chars) env->ReleaseStringUTFChars(text, chars);
    return result;
}
} // namespace

extern "C" JNIEXPORT jlong JNICALL
Java_com_yuanjieflycontr_ml_YoloNcnn_nativeCreate(
    JNIEnv* env,
    jobject /* thiz */,
    jobject asset_manager,
    jstring param_path,
    jstring bin_path
) {
    AAssetManager* mgr = AAssetManager_fromJava(env, asset_manager);
    if (!mgr) {
        __android_log_print(ANDROID_LOG_ERROR, TAG, "AAssetManager_fromJava failed");
        return 0;
    }

    auto* detector = new YoloDetector();
    const std::string param = jstringToStdString(env, param_path);
    const std::string bin = jstringToStdString(env, bin_path);
    if (!detector->load(mgr, param, bin)) {
        delete detector;
        return 0;
    }
    return reinterpret_cast<jlong>(detector);
}

extern "C" JNIEXPORT jfloatArray JNICALL
Java_com_yuanjieflycontr_ml_YoloNcnn_nativeDetect(
    JNIEnv* env,
    jobject /* thiz */,
    jlong handle,
    jobject bitmap,
    jfloat conf_threshold,
    jfloat iou_threshold
) {
    auto* detector = reinterpret_cast<YoloDetector*>(handle);
    if (!detector || !bitmap) {
        return env->NewFloatArray(0);
    }

    const std::vector<Object> objects = detector->detect(env, bitmap, conf_threshold, iou_threshold);
    jfloatArray result = env->NewFloatArray(static_cast<jsize>(objects.size() * 6));
    if (!result) return nullptr;

    std::vector<jfloat> flat;
    flat.reserve(objects.size() * 6);
    for (const auto& obj : objects) {
        flat.push_back(obj.x0);
        flat.push_back(obj.y0);
        flat.push_back(obj.x1);
        flat.push_back(obj.y1);
        flat.push_back(obj.prob);
        flat.push_back(static_cast<jfloat>(obj.label));
    }
    if (!flat.empty()) {
        env->SetFloatArrayRegion(result, 0, static_cast<jsize>(flat.size()), flat.data());
    }
    return result;
}

extern "C" JNIEXPORT void JNICALL
Java_com_yuanjieflycontr_ml_YoloNcnn_nativeDestroy(
    JNIEnv* /* env */,
    jobject /* thiz */,
    jlong handle
) {
    auto* detector = reinterpret_cast<YoloDetector*>(handle);
    delete detector;
}
