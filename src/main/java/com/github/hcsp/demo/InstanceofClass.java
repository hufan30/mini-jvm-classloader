package com.github.hcsp.demo;

import com.github.hcsp.MiniJVMClass;

public class InstanceofClass {
    public static void main(String[] args) throws ClassNotFoundException {
        MyClassLoader myClassLoader = new MyClassLoader();
        final MiniJVMClass klass = myClassLoader.loadClass("com.github.hcsp.demo.SimpleClass");

        // 预期输出false
        System.out.println(klass.newInstance() instanceof SimpleClass);
    }
}
