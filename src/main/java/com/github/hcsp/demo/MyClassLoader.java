package com.github.hcsp.demo;

import com.github.hcsp.MiniJVMClass;
import com.github.hcsp.MiniJVMClassLoader;

public class MyClassLoader extends MiniJVMClassLoader {
    public MyClassLoader() {
        super(new String[]{"target/classes"}, null);
    }

    public MiniJVMClass loadClass(String className) throws ClassNotFoundException {
        return findAndDefineClass(className);
    }
}
