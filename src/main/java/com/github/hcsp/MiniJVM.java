package com.github.hcsp;

import com.github.hcsp.demo.MiniJVMObject;
import com.github.hcsp.demo.MyClassLoader;
import com.github.zxh.classpy.classfile.ClassFile;
import com.github.zxh.classpy.classfile.ClassFileParser;
import com.github.zxh.classpy.classfile.MethodInfo;
import com.github.zxh.classpy.classfile.bytecode.Bipush;
import com.github.zxh.classpy.classfile.bytecode.Instruction;
import com.github.zxh.classpy.classfile.bytecode.InstructionCp1;
import com.github.zxh.classpy.classfile.bytecode.InstructionCp2;
import com.github.zxh.classpy.classfile.constant.ConstantClassInfo;
import com.github.zxh.classpy.classfile.constant.ConstantFieldrefInfo;
import com.github.zxh.classpy.classfile.constant.ConstantMethodrefInfo;
import com.github.zxh.classpy.classfile.constant.ConstantNameAndTypeInfo;
import com.github.zxh.classpy.classfile.constant.ConstantPool;
import com.github.zxh.classpy.classfile.datatype.U1CpIndex;
import com.github.zxh.classpy.common.FilePart;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.Stack;

/**
 * 这是一个用来学习的JVM
 */
public class MiniJVM {
    private String mainClassName;
    private MiniJVMClassLoader appClassLoader;

    public static void main(String[] args) throws ClassNotFoundException {
        new MiniJVM("target/classes", "com.github.hcsp.demo.SameClassLoaderClass").start();
//        new MiniJVM("target/classes", "com.github.hcsp.demo.SimpleClass").start();

    }

    /**
     * 创建一个迷你JVM，使用指定的classpath和main class
     *
     * @param classPath 启动时的classpath，使用{@link java.io.File#pathSeparator}的分隔符，我们支持文件夹
     */
    public MiniJVM(String classPath, String mainClass) {
        this.mainClassName = mainClass;
        this.appClassLoader = new MiniJVMClassLoader(classPath.split(File.pathSeparator), MiniJVMClassLoader.EXT_CLASSLOADER);
    }

    /**
     * 启动并运行该虚拟机
     */
    public void start() throws ClassNotFoundException {
        MiniJVMClass mainClass = appClassLoader.loadClass(mainClassName);

        MethodInfo methodInfo = mainClass.getMethod("main").get(0);

        Stack<StackFrame> methodStack = new Stack<>();

        Object[] localVariablesForMainStackFrame = new Object[methodInfo.getMaxLocals()];
        localVariablesForMainStackFrame[0] = null;

        methodStack.push(new StackFrame(localVariablesForMainStackFrame, methodInfo, mainClass));

        PCRegister pcRegister = new PCRegister(methodStack);

        while (true) {
            Instruction instruction = pcRegister.getNextInstruction();
            if (instruction == null) {
                break;
            }
            switch (instruction.getOpcode()) {
                case getstatic: {
                    int fieldIndex = InstructionCp2.class.cast(instruction).getTargetFieldIndex();
                    ConstantPool constantPool = pcRegister.getTopFrameClassConstantPool();
                    ConstantFieldrefInfo fieldrefInfo = constantPool.getFieldrefInfo(fieldIndex);
                    ConstantClassInfo classInfo = fieldrefInfo.getClassInfo(constantPool);
                    ConstantNameAndTypeInfo nameAndTypeInfo = fieldrefInfo.getFieldNameAndTypeInfo(constantPool);

                    String className = constantPool.getUtf8String(classInfo.getNameIndex());
                    String fieldName = nameAndTypeInfo.getName(constantPool);

                    if ("java/lang/System".equals(className) && "out".equals(fieldName)) {
                        Object field = System.out;
                        pcRegister.getTopFrame().pushObjectToOperandStack(field);
                    } else {
                        throw new IllegalStateException("Not implemented yet!");
                    }
                }
                break;
                case invokestatic: {
                    String className = getClassNameFromInvokeInstruction(instruction, pcRegister.getTopFrameClassConstantPool());
                    String methodName = getMethodNameFromInvokeInstruction(instruction, pcRegister.getTopFrameClassConstantPool());
                    MiniJVMClass classFile = appClassLoader.loadClass(className);
                    MethodInfo targetMethodInfo = classFile.getMethod(methodName).get(0);

                    Object[] localVariables = new Object[targetMethodInfo.getMaxLocals()];

                    // TODO 应该分析方法的参数，从操作数栈上弹出对应数量的参数放在新栈帧的局部变量表中
                    StackFrame newFrame = new StackFrame(localVariables, targetMethodInfo, classFile);
                    methodStack.push(newFrame);
                }
                break;
                case bipush: {
                    Bipush bipush = (Bipush) instruction;
                    pcRegister.getTopFrame().pushObjectToOperandStack(bipush.getOperand());
                }
                break;
                case ireturn: {
                    Object returnValue = pcRegister.getTopFrame().popFromOperandStack();
                    pcRegister.popFrameFromMethodStack();
                    pcRegister.getTopFrame().pushObjectToOperandStack(returnValue);
                }
                break;
                case invokevirtual: {
                    String className = getClassNameFromInvokeInstruction(instruction, pcRegister.getTopFrameClassConstantPool());
                    String methodName = getMethodNameFromInvokeInstruction(instruction, pcRegister.getTopFrameClassConstantPool());
                    if ("java/io/PrintStream".equals(className) && "println".equals(methodName)) {
                        Object param = pcRegister.getTopFrame().popFromOperandStack();
                        Object thisObject = pcRegister.getTopFrame().popFromOperandStack();
                        System.out.println(param);
                    } else if ("com/github/hcsp/demo/MyClassLoader".equals(className)
                            && "loadClass".equals(methodName)) {
                        String classNameParam = (String) pcRegister.getTopFrame().popFromOperandStack();
                        MiniJVMObject thisObject = (MiniJVMObject) pcRegister.getTopFrame().popFromOperandStack();

                        MiniJVMClass klass = ((MyClassLoader) thisObject.getRealJavaObject()).loadClass(classNameParam);
                        pcRegister.getTopFrame().pushObjectToOperandStack(klass);
                    } else if ("com/github/hcsp/MiniJVMClass".equals(className)
                            && "newInstance".equals(methodName)) {
                        MiniJVMClass thisObject = (MiniJVMClass) pcRegister.getTopFrame().popFromOperandStack();
                        pcRegister.getTopFrame().pushObjectToOperandStack(thisObject.newInstance());
                    } else {
                        throw new IllegalStateException("Not implemented yet!");
                    }
                }
                break;
                case _return:
                    pcRegister.popFrameFromMethodStack();
                    break;
                case _new: {
                    String className = getClassNameFromNewOrCheckcastInstruction(instruction, pcRegister.getTopFrameClassConstantPool());
                    MiniJVMClass klass = pcRegister.getTopFrame().getKlass().getClassLoader().loadClass(className);
                    pcRegister.getTopFrame().pushObjectToOperandStack(klass.newInstance());
                }
                break;
                case dup: {
                    pcRegister.getTopFrame().pushObjectToOperandStack(pcRegister.getTopFrame().peekOperandStack());
                    break;
                }
                case invokespecial: {
                    pcRegister.getTopFrame().popFromOperandStack();
                    break;
                }
                case astore_1:
                    pcRegister.getTopFrame().astore(1);
                    break;
                case astore_2:
                    pcRegister.getTopFrame().astore(2);
                    break;
                case aload_1:
                    pcRegister.getTopFrame().aload(1);
                    break;
                case aload_2:
                    pcRegister.getTopFrame().aload(2);
                    break;
                case ldc: {
                    FilePart filePart = InstructionCp1.class.cast(instruction).getParts().get(1);
                    U1CpIndex index = (U1CpIndex) filePart;
                    int constantPoolIndex = index.getValue();
                    String s = pcRegister.getTopFrameClassConstantPool().getConstantDesc(constantPoolIndex);
                    pcRegister.getTopFrame().pushObjectToOperandStack(s);
                    break;
                }
                case checkcast: {
                    String className = getClassNameFromNewOrCheckcastInstruction(instruction, pcRegister.getTopFrameClassConstantPool()).replace('/', '.');
                    MiniJVMClass targetClass = pcRegister.getTopFrame().getKlass().getClassLoader().loadClass(className);
                    MiniJVMObject objectOnStack = (MiniJVMObject) pcRegister.getTopFrame().peekOperandStack();

                    if (!objectOnStack.getKlass().getName().equals(targetClass.getName())
                            || objectOnStack.getKlass().getClassLoader() != targetClass.getClassLoader()) {
                        throw new ClassCastException("Can't cast type " + objectOnStack.getKlass().getName() + " to type " + targetClass.getName());
                    }
                    break;
                }

                default:
                    throw new IllegalStateException("Opcode " + instruction + " not implemented yet!");
            }
        }
    }

    private String getClassNameFromNewOrCheckcastInstruction(Instruction instruction, ConstantPool constantPool) {
        int targetClassIndex = InstructionCp2.class.cast(instruction).getTargetClassIndex();
        ConstantClassInfo classInfo = constantPool.getClassInfo(targetClassIndex);
        return constantPool.getUtf8String(classInfo.getNameIndex());

    }

    private String getClassNameFromInvokeInstruction(Instruction instruction, ConstantPool constantPool) {
        int methodIndex = InstructionCp2.class.cast(instruction).getTargetMethodIndex();
        ConstantMethodrefInfo methodrefInfo = constantPool.getMethodrefInfo(methodIndex);
        ConstantClassInfo classInfo = methodrefInfo.getClassInfo(constantPool);
        return constantPool.getUtf8String(classInfo.getNameIndex());
    }

    private String getMethodNameFromInvokeInstruction(Instruction instruction, ConstantPool constantPool) {
        int methodIndex = InstructionCp2.class.cast(instruction).getTargetMethodIndex();
        ConstantMethodrefInfo methodrefInfo = constantPool.getMethodrefInfo(methodIndex);
        ConstantClassInfo classInfo = methodrefInfo.getClassInfo(constantPool);
        return methodrefInfo.getMethodNameAndType(constantPool).getName(constantPool);
    }

    static class PCRegister {
        Stack<StackFrame> methodStack;

        public PCRegister(Stack<StackFrame> methodStack) {
            this.methodStack = methodStack;
        }

        public StackFrame getTopFrame() {
            return methodStack.peek();
        }

        public ConstantPool getTopFrameClassConstantPool() {
            return getTopFrame().getClassFile().getConstantPool();
        }

        public Instruction getNextInstruction() {
            if (methodStack.isEmpty()) {
                return null;
            } else {
                StackFrame frameAtTop = methodStack.peek();
                return frameAtTop.getNextInstruction();
            }
        }

        public void popFrameFromMethodStack() {
            methodStack.pop();
        }
    }

    static class StackFrame {
        Object[] localVariables;
        Stack<Object> operandStack = new Stack<>();
        MethodInfo methodInfo;
        MiniJVMClass klass;

        int currentInstructionIndex;

        public Instruction getNextInstruction() {
            return methodInfo.getCode().get(currentInstructionIndex++);
        }

        public MiniJVMClass getKlass() {
            return klass;
        }

        public ClassFile getClassFile() {
            return klass.getClassFile();
        }

        public StackFrame(Object[] localVariables, MethodInfo methodInfo, MiniJVMClass klass) {
            this.localVariables = localVariables;
            this.methodInfo = methodInfo;
            this.klass = klass;
        }

        public void pushObjectToOperandStack(Object object) {
            operandStack.push(object);
        }

        public Object popFromOperandStack() {
            return operandStack.pop();
        }

        public Object peekOperandStack() {
            return operandStack.peek();
        }

        public void astore(int i) {
            localVariables[i] = operandStack.pop();
        }

        public void aload(int i) {
            operandStack.push(localVariables[i]);
        }
    }
}
