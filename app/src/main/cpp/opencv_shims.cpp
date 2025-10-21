#include <android/log.h>
#include <cstdlib>
#include <opencv2/core/cvstd.hpp>
#include <opencv2/core/base.hpp>

namespace cv {

// Định nghĩa lại symbol cv::error với đúng chữ ký theo headers đang build
void error(int status, const String& func_name, const char* err_msg, const char* file_name, int line) {
	__android_log_print(ANDROID_LOG_ERROR, "opencv_shim", "cv::error(%d) %s | %s (%s:%d)", status,
		func_name.c_str(), err_msg ? err_msg : "", file_name ? file_name : "", line);
	abort();
}

} // namespace cv
