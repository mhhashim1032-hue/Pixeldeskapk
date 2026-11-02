#include <jni.h>
#include <errno.h>
#include <fcntl.h>
#include <signal.h>
#include <stdint.h>
#include <stdlib.h>
#include <string.h>
#include <cctype>
#include <sys/ioctl.h>
#include <sys/types.h>
#include <sys/wait.h>
#include <termios.h>
#include <unistd.h>
#include <vector>
#include <string>
#include <unordered_map>

extern char **environ;

namespace {
void throw_io(JNIEnv *env, const char *message) {
    jclass type = env->FindClass("java/io/IOException");
    if (type != nullptr) env->ThrowNew(type, message);
}

std::string from_java(JNIEnv *env, jstring value) {
    const char *chars = env->GetStringUTFChars(value, nullptr);
    if (chars == nullptr) return {};
    std::string result(chars);
    env->ReleaseStringUTFChars(value, chars);
    return result;
}

bool read_array(JNIEnv *env, jobjectArray source, std::vector<std::string> &storage) {
    if (source == nullptr) return false;
    const jsize count = env->GetArrayLength(source);
    if (count < 0 || count > 256) return false;
    storage.reserve(static_cast<size_t>(count));
    for (jsize i = 0; i < count; ++i) {
        auto item = static_cast<jstring>(env->GetObjectArrayElement(source, i));
        if (item == nullptr) return false;
        std::string value = from_java(env, item);
        env->DeleteLocalRef(item);
        if (env->ExceptionCheck() || value.size() > 8192) return false;
        storage.push_back(std::move(value));
    }
    return true;
}

jlong pack_handle(pid_t pid, int master) {
    const uint64_t value = (static_cast<uint64_t>(static_cast<uint32_t>(pid)) << 32) |
                           static_cast<uint32_t>(master);
    return static_cast<jlong>(value);
}
pid_t handle_pid(jlong handle) {
    return static_cast<pid_t>(static_cast<uint32_t>(static_cast<uint64_t>(handle) >> 32));
}
int handle_fd(jlong handle) {
    return static_cast<int>(static_cast<uint32_t>(static_cast<uint64_t>(handle)));
}
}

extern "C" JNIEXPORT jlong JNICALL
Java_com_pixeldesk_app_NativePtyBridge_spawn(
    JNIEnv *env, jclass, jstring executable_value, jobjectArray arguments,
    jobjectArray environment, jint rows, jint columns) {
    if (executable_value == nullptr || rows < 2 || rows > 300 ||
        columns < 2 || columns > 500) {
        throw_io(env, "Invalid PTY launch parameters.");
        return 0;
    }
    const std::string executable = from_java(env, executable_value);
    if (env->ExceptionCheck() || executable.empty() || executable.size() > 4096 ||
        executable.front() != '/') {
        throw_io(env, "PTY executable must be a valid absolute path.");
        return 0;
    }
    std::vector<std::string> argument_storage;
    std::vector<std::string> environment_storage;
    if (!read_array(env, arguments, argument_storage) ||
        !read_array(env, environment, environment_storage) ||
        environment_storage.size() > 128) {
        throw_io(env, "Invalid or oversized PTY argument/environment list.");
        return 0;
    }

    std::vector<char *> argv;
    argv.reserve(argument_storage.size() + 2);
    argv.push_back(const_cast<char *>(executable.c_str()));
    for (auto &item : argument_storage) argv.push_back(const_cast<char *>(item.c_str()));
    argv.push_back(nullptr);

    std::unordered_map<std::string, std::string> env_map;
    for (char **entry = environ; entry != nullptr && *entry != nullptr; ++entry) {
        std::string value(*entry);
        const size_t separator = value.find('=');
        if (separator != std::string::npos) env_map[value.substr(0, separator)] = value.substr(separator + 1);
    }
    for (const auto &item : environment_storage) {
        const size_t separator = item.find('=');
        if (separator == std::string::npos || separator == 0) {
            throw_io(env, "Invalid PTY environment entry.");
            return 0;
        }
        const std::string key = item.substr(0, separator);
        if (!(std::isalpha(static_cast<unsigned char>(key[0])) || key[0] == '_') ||
            !std::all_of(key.begin(), key.end(), [](unsigned char ch) {
                return std::isalnum(ch) || ch == '_';
            })) {
            throw_io(env, "Invalid PTY environment variable name.");
            return 0;
        }
        env_map[key] = item.substr(separator + 1);
    }
    std::vector<std::string> env_storage;
    env_storage.reserve(env_map.size());
    for (const auto &entry : env_map) env_storage.push_back(entry.first + "=" + entry.second);
    std::vector<char *> envp;
    envp.reserve(env_storage.size() + 1);
    for (auto &item : env_storage) envp.push_back(const_cast<char *>(item.c_str()));
    envp.push_back(nullptr);

    int master = posix_openpt(O_RDWR | O_NOCTTY | O_CLOEXEC);
    if (master < 0 || grantpt(master) != 0 || unlockpt(master) != 0) {
        if (master >= 0) close(master);
        throw_io(env, "Could not allocate a POSIX pseudo-terminal.");
        return 0;
    }
    char slave_name[256] = {};
    if (ptsname_r(master, slave_name, sizeof(slave_name)) != 0) {
        close(master);
        throw_io(env, "Could not resolve the PTY slave device.");
        return 0;
    }

    struct winsize window = {};
    window.ws_row = static_cast<unsigned short>(rows);
    window.ws_col = static_cast<unsigned short>(columns);
    ioctl(master, TIOCSWINSZ, &window);

    const pid_t child = fork();
    if (child < 0) {
        close(master);
        throw_io(env, "Could not fork the Linux terminal process.");
        return 0;
    }
    if (child == 0) {
        if (setsid() < 0) _exit(126);
        int slave = open(slave_name, O_RDWR);
        if (slave < 0) _exit(126);
        if (ioctl(slave, TIOCSCTTY, 0) != 0) _exit(126);
        if (dup2(slave, STDIN_FILENO) < 0 ||
            dup2(slave, STDOUT_FILENO) < 0 ||
            dup2(slave, STDERR_FILENO) < 0) _exit(126);
        if (slave > STDERR_FILENO) close(slave);
        close(master);
        execve(executable.c_str(), argv.data(), envp.data());
        _exit(errno == ENOENT ? 127 : 126);
    }

    return pack_handle(child, master);
}

extern "C" JNIEXPORT jint JNICALL
Java_com_pixeldesk_app_NativePtyBridge_read(
    JNIEnv *env, jclass, jlong handle, jbyteArray destination, jint offset, jint length) {
    if (destination == nullptr || offset < 0 || length <= 0 || length > 1024 * 1024) return -1;
    const jsize array_length = env->GetArrayLength(destination);
    if (offset > array_length || length > array_length - offset) return -1;
    std::vector<jbyte> buffer(static_cast<size_t>(length));
    ssize_t count;
    do { count = ::read(handle_fd(handle), buffer.data(), buffer.size()); } while (count < 0 && errno == EINTR);
    if (count > 0) env->SetByteArrayRegion(destination, offset, static_cast<jsize>(count), buffer.data());
    if (count < 0 && errno != EIO) throw_io(env, "PTY read failed.");
    return static_cast<jint>(count);
}

extern "C" JNIEXPORT jint JNICALL
Java_com_pixeldesk_app_NativePtyBridge_write(
    JNIEnv *env, jclass, jlong handle, jbyteArray source, jint offset, jint length) {
    if (source == nullptr || offset < 0 || length <= 0 || length > 1024 * 1024) return -1;
    const jsize array_length = env->GetArrayLength(source);
    if (offset > array_length || length > array_length - offset) return -1;
    std::vector<jbyte> buffer(static_cast<size_t>(length));
    env->GetByteArrayRegion(source, offset, length, buffer.data());
    if (env->ExceptionCheck()) return -1;
    ssize_t count;
    do { count = ::write(handle_fd(handle), buffer.data(), buffer.size()); } while (count < 0 && errno == EINTR);
    if (count < 0 && errno != EIO) throw_io(env, "PTY write failed.");
    return static_cast<jint>(count);
}

extern "C" JNIEXPORT jboolean JNICALL
Java_com_pixeldesk_app_NativePtyBridge_resize(
    JNIEnv *, jclass, jlong handle, jint rows, jint columns) {
    if (rows < 2 || rows > 300 || columns < 2 || columns > 500) return JNI_FALSE;
    struct winsize window = {};
    window.ws_row = static_cast<unsigned short>(rows);
    window.ws_col = static_cast<unsigned short>(columns);
    if (ioctl(handle_fd(handle), TIOCSWINSZ, &window) != 0) return JNI_FALSE;
    return kill(-handle_pid(handle), SIGWINCH) == 0 ? JNI_TRUE : JNI_FALSE;
}

extern "C" JNIEXPORT jboolean JNICALL
Java_com_pixeldesk_app_NativePtyBridge_sendSignal(
    JNIEnv *, jclass, jlong handle, jint signal_number) {
    if (signal_number <= 0 || signal_number >= NSIG) return JNI_FALSE;
    return kill(-handle_pid(handle), signal_number) == 0 ? JNI_TRUE : JNI_FALSE;
}

extern "C" JNIEXPORT jboolean JNICALL
Java_com_pixeldesk_app_NativePtyBridge_close(JNIEnv *, jclass, jlong handle) {
    const pid_t child = handle_pid(handle);
    const int master = handle_fd(handle);
    close(master); // Closing the PTY master also sends SIGHUP to the controlling session.
    if (child > 0) {
        kill(-child, SIGTERM);
        int status = 0;
        for (int attempt = 0; attempt < 20; ++attempt) {
            const pid_t result = waitpid(child, &status, WNOHANG);
            if (result == child || (result < 0 && errno == ECHILD)) return JNI_TRUE;
            usleep(10 * 1000);
        }
        kill(-child, SIGKILL);
        while (waitpid(child, &status, 0) < 0 && errno == EINTR) {}
    }
    return JNI_TRUE;
}
