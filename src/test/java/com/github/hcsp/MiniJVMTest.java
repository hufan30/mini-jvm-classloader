package com.github.hcsp;

import com.github.blindpirate.extensions.CaptureSystemOutput;
import com.github.blindpirate.extensions.CaptureSystemOutputExtension;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import java.io.File;

@ExtendWith(CaptureSystemOutputExtension.class)
public class MiniJVMTest {
    private String classPath = new File("target/classes").getAbsolutePath();

    @Test
    @CaptureSystemOutput
    public void instanceOfClassTest(CaptureSystemOutput.OutputCapture capture) throws Exception {
        capture.expect(Matchers.containsString("false"));
        new MiniJVM(classPath, "com.github.hcsp.demo.InstanceofClass").start();
    }
}
