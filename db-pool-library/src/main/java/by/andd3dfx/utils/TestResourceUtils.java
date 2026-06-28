package by.andd3dfx.utils;

import lombok.SneakyThrows;
import lombok.experimental.UtilityClass;
import org.apache.commons.io.FileUtils;

import java.io.File;
import java.nio.charset.StandardCharsets;

@UtilityClass
public class TestResourceUtils {

    public static final String RESOURCE_BASE_PATH = "src/test/resources/";
    // 1 KB
    public static final int TINY_TEST_DOCUMENT_BYTES = 1024;

    /**
     * Reads all characters from a resource into a string, decoding from bytes to characters
     * using the {@link StandardCharsets#UTF_8 UTF-8} {@link java.nio.charset.Charset charset}.
     *
     * <p>If the resource path is relative, it is assumed to be relative to {@link #RESOURCE_BASE_PATH}.
     * If the resource path is absolute, it is assumed to be absolute.
     */
    @SneakyThrows
    public String readToString(String resourcePath) {
        if (resourcePath.startsWith("/")) {
            return readStringInternal(new File(resourcePath));
        } else {
            return readStringInternal(new File(TestResourceUtils.RESOURCE_BASE_PATH, resourcePath));
        }
    }

    /**
     * Reads all the bytes from a resource.
     *
     * <p>If the resource path is relative, it is assumed to be relative to {@link #RESOURCE_BASE_PATH}.
     * If the resource path is absolute, it is assumed to be absolute.
     */
    public byte[] readToBytes(String resourcePath) {
        if (resourcePath.startsWith("/")) {
            return readAllBytesInternal(new File(resourcePath));
        } else {
            return readAllBytesInternal(new File(TestResourceUtils.RESOURCE_BASE_PATH, resourcePath));
        }
    }

    @SneakyThrows
    private static String readStringInternal(File file) {
        return FileUtils.readFileToString(file, StandardCharsets.UTF_8);
    }

    @SneakyThrows
    private static byte[] readAllBytesInternal(File file) {
        return FileUtils.readFileToByteArray(file);
    }

    public static void writeResource(String resource, String data) {
        if (resource.startsWith("/")) {
            TestResourceUtils.writeResourceInternal(resource, data);
        } else {
            TestResourceUtils.writeResource(RESOURCE_BASE_PATH, resource, data);
        }
    }

    @SneakyThrows
    private static void writeResource(String parent, String path, String data) {
        FileUtils.write(new File(parent, path), data, StandardCharsets.UTF_8);
    }

    @SneakyThrows
    private static void writeResourceInternal(String path, String data) {
        FileUtils.write(new File(path), data, StandardCharsets.UTF_8);
    }

}
