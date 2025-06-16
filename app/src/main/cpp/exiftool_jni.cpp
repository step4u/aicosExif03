#include <jni.h>
#include <android/log.h>
#include <string>
#include <unistd.h>
#include <fcntl.h>

#include <EXTERN.h>
#include <perl.h>

#define  LOG_TAG  "ExifToolJNI"
#define  LOGI(...)  __android_log_print(ANDROID_LOG_INFO , LOG_TAG, __VA_ARGS__)
#define  LOGE(...)  __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

extern "C" char **environ;

/* ─────────────────────────────────────────────────────────── */

extern "C"
JNIEXPORT jstring JNICALL
Java_com_example_aicosexif03_ExifTool_runExifTool(
        JNIEnv *env, jclass,
        jstring imagePath_, jstring workDir_)
{
    /* ───── 0. Java → C 문자열 ───── */
    const char *img = env->GetStringUTFChars(imagePath_, nullptr);
    const char *dir = env->GetStringUTFChars(workDir_,  nullptr);

    /* ───── 1. 경로 보존 ───── */
//    std::string libPath    = std::string(dir) + "/lib";
//    std::string perlLibPath    = "/data/user/0/com.example.aicosexif03/files/perl/perl5_aarch64/lib";
//    std::string scriptPath = std::string(dir) + "/exiftool.pl";

//    std::string perlLibPath = "/data/user/0/com.example.aicosexif03/files/perl5_aarch64/lib";
    std::string perlLibPath = "/data/user/0/com.example.aicosexif03/files/perl/lib";
    std::string scriptPath = "/data/user/0/com.example.aicosexif03/files/exiftool_files/exiftool.pl";


    /* -I<lib> 한 인수로 결합 */
//    std::string incArg = "-I" + libPath;
    std::string perlIncArc = "-I" + perlLibPath;

    /* ───── 2. argv 배열 (char*) ───── */
    char *argv[] = {
            const_cast<char*>(""),                 // argv[0]
//            const_cast<char*>(incArg.c_str()),     // -I<lib>
            const_cast<char*>(perlIncArc.c_str()),     // -I<lib>
            const_cast<char*>(scriptPath.c_str()), // exiftool.pl
            const_cast<char*>(img),                // 이미지 경로
            nullptr
    };
    int argc = 4;
    char **argv_ptr = argv;

    /* ───── 3. STDOUT → 파이프 리다이렉트 ───── */
    int fd[2];
    if (pipe(fd) == -1) {
        env->ThrowNew(env->FindClass("java/io/IOException"), "pipe() failed");
        return nullptr;
    }
    int stdout_backup = dup(STDOUT_FILENO);
    dup2(fd[1], STDOUT_FILENO);   // STDOUT → 파이프 write
    close(fd[1]);                 // write end 복사본만 유지

    /* ───── 4. Perl 인터프리터 구동 ───── */
    PERL_SYS_INIT3(&argc, &argv_ptr, &environ);

    PerlInterpreter *my_perl = perl_alloc();
    perl_construct(my_perl);

    int parse_ret = perl_parse(my_perl, nullptr, argc, argv_ptr, nullptr);
    if (parse_ret != 0) {
        LOGE("perl_parse ERR: %s", SvPV_nolen(ERRSV));   // ★ 핵심
    }
    int run_ret   = (parse_ret == 0) ? perl_run(my_perl) : -1;

    /* ───── 5. STDOUT 복구 ───── */
    fflush(stdout);
    dup2(stdout_backup, STDOUT_FILENO);
    close(stdout_backup);

    /* ───── 6. 파이프 read ───── */
    std::string output;
    char  buf[4096];
    ssize_t n;
    while ((n = read(fd[0], buf, sizeof(buf))) > 0) {
        output.append(buf, n);
    }
    close(fd[0]);

    /* ───── 7. 종료 & 오류 처리 ───── */
    perl_destruct(my_perl);
    perl_free(my_perl);
    PERL_SYS_TERM();

    env->ReleaseStringUTFChars(imagePath_, img);
    env->ReleaseStringUTFChars(workDir_,  dir);

    if (parse_ret != 0 || run_ret != 0) {
        std::string err = "exiftool failed: ";
        err += output.empty() ? "unknown error" : output;
        env->ThrowNew(env->FindClass("java/lang/RuntimeException"), err.c_str());
        return nullptr;
    }

    return env->NewStringUTF(output.c_str());   // JSON 문자열 반환
}
