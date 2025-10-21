//#include <jni.h>
//#include <string>
//#include <math.h>
//#include <numeric>
//#include <opencv2/core/core.hpp>
//#include <opencv2/imgproc.hpp> // Cần cho cv::cvtColor, cv::resize, cv::findHomography, cv::warpPerspective
//#include <opencv2/imgproc/imgproc_c.h>
//#include <opencv2/calib3d.hpp> // Cần cho cv::solvePnP và cv::projectPoints
//#include <opencv2/imgcodecs.hpp> // Cần cho cv::imdecode
//
//#include <aruco3/aruco.h> // Thư viện phát hiện marker
//#include <aruco3/cameraparameters.h>
//
//#include <android/log.h>
//
//#define  LOG_TAG    "jnilog"
//#define  ALOG(...)  __android_log_print(ANDROID_LOG_INFO,LOG_TAG,__VA_ARGS__)
//
//using namespace std;
//
//// =================================================================================
//// --- 1. KHAI BÁO BIẾN TOÀN CỤC ---
//// =================================================================================
//
//cv::Mat cameraMatrix;
//cv::Mat distCoeffs;
//double markerLength = 0.05;
//aruco::MarkerDetector makerDetector;
//std::vector<cv::Point3f> marker_model_points;
//cv::Mat overlayImg; // Biến toàn cục để lưu trữ ảnh overlay đã được giải mã
//
//// =================================================================================
//// --- 2. CÁC HÀM HỖ TRỢ ---
//// =================================================================================
//
//void initArucoDetector() {
//    makerDetector.getParameters().setDetectionMode((aruco::DetectionMode) 1, 100 / 1000);
//    makerDetector.getParameters().setCornerRefinementMethod((aruco::CornerRefinementMethod) 2);
//    makerDetector.getParameters().detectEnclosedMarkers(false);
//    makerDetector.getParameters().ThresHold = 120;
//    makerDetector.setDictionary((aruco::Dictionary::DICT_TYPES) 4, 0);
//    makerDetector.getParameters().error_correction_rate = 3.0;
//}
//
//void createMarkerModelPoints(double length) {
//    float half_length = (float)length / 2.0f;
//    marker_model_points.clear();
//    // 0: top-left
//    marker_model_points.push_back(cv::Point3f(-half_length, half_length, 0));
//    // 1: top-right
//    marker_model_points.push_back(cv::Point3f(half_length, half_length, 0));
//    // 2: bottom-right
//    marker_model_points.push_back(cv::Point3f(half_length, -half_length, 0));
//    // 3: bottom-left
//    marker_model_points.push_back(cv::Point3f(-half_length, -half_length, 0));
//}
//
//// Hàm trộn ảnh (Alpha Blending)
//void overlayImage(const cv::Mat& warped_overlay, cv::Mat& imgDisplay, const cv::Rect& roi) {
//    if (roi.area() <= 0) return;
//
//    cv::Rect intersection_roi = roi & cv::Rect(0, 0, imgDisplay.cols, imgDisplay.rows);
//
//    if (intersection_roi.area() <= 0) return;
//
//    cv::Mat roiDisplay = imgDisplay(intersection_roi);
//    cv::Mat roiWarped = warped_overlay(intersection_roi);
//
//    for (int y = 0; y < intersection_roi.height; ++y) {
//        for (int x = 0; x < intersection_roi.width; ++x) {
//
//            // Đảm bảo chỉ số nằm trong giới hạn
//            if (y >= roiWarped.rows || x >= roiWarped.cols) continue;
//
//            cv::Vec4b& srcPixel = roiWarped.at<cv::Vec4b>(y, x);
//            cv::Vec4b& dstPixel = roiDisplay.at<cv::Vec4b>(y, x);
//
//            float alpha = srcPixel[3] / 255.0f; // Kênh Alpha của ảnh overlay (BGRA)
//
//            if (alpha > 0.001f) {
//                dstPixel[0] = (uchar)(srcPixel[0] * alpha + dstPixel[0] * (1.0f - alpha)); // B
//                dstPixel[1] = (uchar)(srcPixel[1] * alpha + dstPixel[1] * (1.0f - alpha)); // G
//                dstPixel[2] = (uchar)(srcPixel[2] * alpha + dstPixel[2] * (1.0f - alpha)); // R
//            }
//        }
//    }
//}
//
//// =================================================================================
//// --- 3. CÁC HÀM JNI ---
//// =================================================================================
//
//// HÀM JNI 1: Thiết lập Ảnh Overlay
//extern "C"
//JNIEXPORT void JNICALL
//Java_com_tuielectronics_aruco_1demo_CameraProcess_setOverlayImage(
//        JNIEnv *env, jobject /* this */,
//        jbyteArray imageData, jint width, jint height) {
//
//    jbyte *pData = env->GetByteArrayElements(imageData, 0);
//    jsize len = env->GetArrayLength(imageData);
//
//    cv::Mat rawData(1, len, CV_8UC1, (unsigned char*)pData);
//    overlayImg = cv::imdecode(rawData, cv::IMREAD_UNCHANGED);
//
//    // Chuyển đổi sang BGRA (CV_8UC4) cho Alpha Blending
//    if (overlayImg.channels() == 3) {
//        cv::cvtColor(overlayImg, overlayImg, cv::COLOR_BGR2BGRA);
//    } else if (overlayImg.channels() == 1) {
//        cv::cvtColor(overlayImg, overlayImg, cv::COLOR_GRAY2BGRA);
//    }
//
//    ALOG("Overlay image loaded in C++: %dx%d, channels: %d", overlayImg.cols, overlayImg.rows, overlayImg.channels());
//
//    env->ReleaseByteArrayElements(imageData, pData, 0);
//}
//
//
//// HÀM JNI 2: Thiết lập Tham số Camera
//extern "C"
//JNIEXPORT void JNICALL
//Java_com_tuielectronics_aruco_1demo_CameraProcess_setCameraParameters(
//        JNIEnv *env, jobject /* this */,
//        jdoubleArray jCameraMatrix, jdoubleArray jDistCoeffs, jdouble jMarkerLength) {
//
//    jdouble *camData = env->GetDoubleArrayElements(jCameraMatrix, 0);
//    jdouble *distData = env->GetDoubleArrayElements(jDistCoeffs, 0);
//
//    cameraMatrix = cv::Mat(3, 3, CV_64FC1, camData).clone();
//    distCoeffs = cv::Mat(1, 5, CV_64FC1, distData).clone();
//
//    markerLength = jMarkerLength;
//    createMarkerModelPoints(markerLength);
//
//    env->ReleaseDoubleArrayElements(jCameraMatrix, camData, 0);
//    env->ReleaseDoubleArrayElements(jDistCoeffs, distData, 0);
//
//    ALOG("Camera parameters set: fx=%.2f, fy=%.2f, markerLength=%.3f",
//         cameraMatrix.at<double>(0, 0), cameraMatrix.at<double>(1, 1), markerLength);
//}
//
//// HÀM JNI 3: Xử Lý Chính
//extern "C" {
//
//JNIEXPORT jdoubleArray JNICALL
//Java_com_tuielectronics_aruco_1demo_CameraProcess_openCVProcess(
//        JNIEnv *env, jobject /* this */,
//        jint width, jint height,
//        jbyteArray NV21FrameData,
//        jintArray outPixels, jint pixel_width, jint pixel_height) {
//
//    jbyte *pNV21FrameData = env->GetByteArrayElements(NV21FrameData, 0);
//    jint *poutPixels = env->GetIntArrayElements(outPixels, 0);
//
//    vector<double> arr;
//    vector<aruco::Marker> makers_detected;
//    cv::Mat rvec_mat, tvec_mat;
//
//    if (pNV21FrameData != nullptr) {
//        cv::Mat mGray(height, width, CV_8UC1, (unsigned char *)pNV21FrameData);
//        cv::Mat mResult(pixel_height, pixel_width, CV_8UC4, (unsigned char *)poutPixels);
//
//        makers_detected = makerDetector.detect(mGray);
//
//        cv::Mat imgDisplay;
//        cv::cvtColor(mGray, imgDisplay, cv::COLOR_GRAY2BGRA);
//
//        for (size_t i = 0; i < makers_detected.size(); ++i) {
//            auto & marker = makers_detected[i];
//
//            // ----------------------------------------------------
//            // ỨNG DỤNG HOMOGRAPHY (GẮN ẢNH 2D THEO PHỐI CẢNH)
//            // ----------------------------------------------------
//            if (!overlayImg.empty()) {
//
//                std::vector<cv::Point2f> src_corners(4);
//                src_corners[0] = cv::Point2f(0.0f, 0.0f);
//                src_corners[1] = cv::Point2f(overlayImg.cols, 0.0f);
//                src_corners[2] = cv::Point2f(overlayImg.cols, overlayImg.rows);
//                src_corners[3] = cv::Point2f(0.0f, overlayImg.rows);
//
//                std::vector<cv::Point2f> dst_corners(4);
//                for (int j = 0; j < 4; j++) {
//                    dst_corners[j] = cv::Point2f(marker[j].x, marker[j].y);
//                }
//
//                cv::Mat H = cv::findHomography(src_corners, dst_corners);
//                cv::Mat warped_overlay(imgDisplay.size(), CV_8UC4, cv::Scalar(0, 0, 0, 0));
//                cv::warpPerspective(overlayImg, warped_overlay, H, imgDisplay.size());
//
//                cv::Rect roi = cv::boundingRect(dst_corners);
//                overlayImage(warped_overlay, imgDisplay, roi);
//            }
//
//            // ----------------------------------------------------
//            // ƯỚC TÍNH POSE 3D VÀ TRẢ VỀ JNI
//            // ----------------------------------------------------
//
//            arr.push_back(double(marker.id));           // 0: ID marker
//
//            std::vector<cv::Point2f> image_points;
//            for (int j = 0; j < 4; ++j) {
//                image_points.push_back(cv::Point2f(marker[j].x, marker[j].y));
//            }
//
//            if (!cameraMatrix.empty() && marker_model_points.size() == 4) {
//                cv::solvePnP(marker_model_points, image_points, cameraMatrix, distCoeffs, rvec_mat, tvec_mat, false, cv::SOLVEPNP_IPPE);
//            }
//
//            if (!rvec_mat.empty() && rvec_mat.depth() == CV_64F) {
//                // Vector Quay (3 giá trị)
//                arr.push_back(rvec_mat.at<double>(0));             // 1: rvec X
//                arr.push_back(rvec_mat.at<double>(1));             // 2: rvec Y
//                arr.push_back(rvec_mat.at<double>(2));             // 3: rvec Z
//
//                // Vector Tịnh tiến (3 giá trị)
//                arr.push_back(tvec_mat.at<double>(0));             // 4: tvec X
//                arr.push_back(tvec_mat.at<double>(1));             // 5: tvec Y
//                arr.push_back(tvec_mat.at<double>(2));             // 6: tvec Z
//
//                // Vẽ bounding box (2D)
////                marker.draw(imgDisplay, cv::Scalar(0, 255, 0, 255), 2, true, false);
//
//                // Vẽ trục tọa độ thủ công
//                double axis_length = markerLength / 2.0;
//                std::vector<cv::Point3f> axis_points;
//                axis_points.push_back(cv::Point3f(0, 0, 0));
//                axis_points.push_back(cv::Point3f(axis_length, 0, 0));
//                axis_points.push_back(cv::Point3f(0, axis_length, 0));
//                axis_points.push_back(cv::Point3f(0, 0, -axis_length));
//
////                std::vector<cv::Point2f> projected_points;
////                cv::projectPoints(axis_points, rvec_mat, tvec_mat, cameraMatrix, distCoeffs, projected_points);
////
////                if (projected_points.size() == 4) {
////                    cv::Point2f center = projected_points[0];
////                    cv::line(imgDisplay, center, projected_points[1], cv::Scalar(255, 0, 0, 255), 2); // X (Red)
////                    cv::line(imgDisplay, center, projected_points[2], cv::Scalar(0, 255, 0, 255), 2); // Y (Green)
////                    cv::line(imgDisplay, center, projected_points[3], cv::Scalar(0, 0, 255, 255), 2); // Z (Blue)
////                }
//            } else {
//                for(int k = 0; k < 6; ++k) arr.push_back(0.0);
//            }
//
//            for (int j = 0; j < 4; j++) {
//                arr.push_back(marker[j].x);
//                arr.push_back(marker[j].y);
//            }
//        }
//
//        // Logic Resize
//        if (imgDisplay.cols > 0 && imgDisplay.rows > 0) {
//            double scale_x = (double)pixel_width / imgDisplay.cols;
//            double scale_y = (double)pixel_height / imgDisplay.rows;
//            double scale = std::min(scale_x, scale_y);
//
//            int new_width = (int)(imgDisplay.cols * scale);
//            int new_height = (int)(imgDisplay.rows * scale);
//
//            cv::Mat resized_img;
//            cv::resize(imgDisplay, resized_img, cv::Size(new_width, new_height), 0, 0, cv::INTER_LINEAR);
//
//            mResult.setTo(cv::Scalar(0, 0, 0, 255));
//
//            int start_x = (pixel_width - new_width) / 2;
//            int start_y = (pixel_height - new_height) / 2;
//
//            cv::Rect roi(start_x, start_y, new_width, new_height);
//            cv::Mat destinationROI = mResult(roi);
//            resized_img.copyTo(destinationROI);
//        }
//
//        env->ReleaseByteArrayElements(NV21FrameData, pNV21FrameData, 0);
//    }
//
//    int arr_size = arr.size();
//    jdoubleArray result = env->NewDoubleArray(arr_size);
//
//    if (arr_size > 0) {
//        env->SetDoubleArrayRegion(result, 0, arr_size, arr.data());
//    }
//
//    env->ReleaseIntArrayElements(outPixels, poutPixels, 0);
//    return result;
//}
//
//}