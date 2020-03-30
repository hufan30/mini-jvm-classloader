package com.github.hcsp;

import com.github.zxh.classpy.classfile.ClassFileParser;
import org.apache.commons.io.IOUtils;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

public class MiniJVMClassLoader {
    public static final MiniJVMClassLoader BOOTSTRAP_CLASSLOADER
            = new MiniJVMClassLoader(new String[]{System.getProperty("java.home") + "/lib/rt.jar"}, null);
    public static final MiniJVMClassLoader EXT_CLASSLOADER
            = new MiniJVMClassLoader(
            Stream.of(new File(System.getProperty("java.home") + "/lib/ext").listFiles())
                    .filter(File::isFile)
                    .filter(f -> f.getName().endsWith(".jar"))
                    .map(File::getName)
                    .toArray(String[]::new),
            BOOTSTRAP_CLASSLOADER);

    // Bootstrap类加载器 从 rt.jar
    // Ext类加载器 ext/
    // 应用类加载器 -classpath
    private Map<String, MiniJVMClass> loadedClasses = new ConcurrentHashMap<>();

    // 不一定是-classpath
    // 每个entry是一个jar包或者文件夹
    // 对于Bootstrap类加载器它是 rt.jar
    // 对于Ext类加载器他是 ext/目录下的所有jar包
    // 对于AppClassLoader 它是 -classpath传入的东西
    private String[] classPath;

    // null代表启动类加载器
    private MiniJVMClassLoader parent;

    public MiniJVMClassLoader(String[] classPath, MiniJVMClassLoader parent) {
        this.classPath = classPath;
        this.parent = parent;
    }

    public MiniJVMClass loadClass(String className) throws ClassNotFoundException {
        if (loadedClasses.containsKey(className)) {
            return loadedClasses.get(className);
        }

        MiniJVMClass result = null;

        try {
            if (parent == null) {
                // 我是启动类加载器
                result = findAndDefineClass(className);
            } else {
                result = parent.loadClass(className);
            }
        } catch (ClassNotFoundException ignored) {
            if (parent == null) {
                throw ignored;
            }
        }

        if (result == null && parent != null) {
            // 父亲没找到，尝试自己加载
            result = findAndDefineClass(className);
        }

        loadedClasses.put(className, result);
        return result;
    }

    protected MiniJVMClass findAndDefineClass(String className) throws ClassNotFoundException {
        byte[] bytes = findClassBytes(className);
        return defineClass(className, bytes);
    }

    protected MiniJVMClass defineClass(String className, byte[] bytes) {
        return new MiniJVMClass(className, this, new ClassFileParser().parse(bytes));
    }

    private byte[] findClassBytes(String className) throws ClassNotFoundException {
        String path = className.replace('.', '/') + ".class";
        for (String entry : classPath) {
            if (new File(entry).isDirectory()) {
                try {
                    return Files.readAllBytes(new File(entry, path).toPath());
                } catch (IOException ignored) {
                }
            } else if (entry.endsWith(".jar")) {
                try {
                    return readBytesFromJar(entry, path);
                } catch (IOException ignored) {
                }
            }
        }
        throw new ClassNotFoundException(className);
    }

    private byte[] readBytesFromJar(String jar, String path) throws IOException {
        ZipFile zipFile = new ZipFile(jar);
        ZipEntry entry = zipFile.getEntry(path);
        if (entry == null) {
            throw new IOException("Not found: " + path);
        }

        InputStream is = zipFile.getInputStream(entry);
        return IOUtils.toByteArray(is);
    }
}

