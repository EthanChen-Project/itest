/**
 * <pre>
 * The MIT License (MIT)
 * 
 * Copyright (c) 2014 Grzegorz Kochański
 * 
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 * 
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 * 
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 * </pre>
 */
package org.itest.executor;

import java.lang.reflect.InvocationTargetException;
import java.util.Collection;

import org.itest.ITestConfig;
import org.itest.ITestExecutor;
import org.itest.definition.ITestDefinition;
import org.itest.execution.ITestMethodExecutionResult;
import org.itest.verify.ITestFieldVerificationResult;

public class ITestExecutorUtil {

    public static ITestExecutor buildExecutor(ITestConfig iTestExecutorConfig) {
        return new ITestExecutorImpl(iTestExecutorConfig);
    }

    private static class ITestExecutorImpl implements ITestExecutor {

        private final ITestConfig itestConfig;

        public ITestExecutorImpl(ITestConfig iTestExecutorConfig) {
            this.itestConfig = iTestExecutorConfig;
        }

        @Override
        public String performTestsFor(Class<?>... classes) {
            Collection<ITestDefinition> iTestFlowDefinitions = itestConfig.getITestDefinitionFactory().buildTestFlowDefinitions(classes);
            StringBuilder sb = new StringBuilder();
            for (ITestDefinition iTestPathDefinition : iTestFlowDefinitions) {
                try {
                    ITestMethodExecutionResult executionData = itestConfig.getITestMethodExecutor().execute(iTestPathDefinition);
                    Collection<ITestFieldVerificationResult> verificationResult = itestConfig.getITestExecutionVerifier().verify(
                            iTestPathDefinition.getITestMethod().toString(), executionData, iTestPathDefinition.getVeryficationParams());
                    for (ITestFieldVerificationResult res : verificationResult) {
                        if ( !res.isSuccess() ) {
                            sb.append(res).append('\n');
                        }
                    }
                } catch (InvocationTargetException e) {
                    sb.append(iTestPathDefinition.getITestMethod().toString()).append(e.getTargetException()).append('\n');
                    StackTraceElement[] trace = e.getTargetException().getStackTrace();
                    for (int i = 0; i < trace.length; i++) {
                        sb.append("\tat ").append(trace[i]).append('\n');
                    }

                }

            }
            return sb.toString();
        }
    }

}
