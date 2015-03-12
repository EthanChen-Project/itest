package org.itest.test.issue;

import org.hamcrest.CoreMatchers;
import org.itest.ITestExecutor;
import org.itest.annotation.ITest;
import org.itest.annotation.ITests;
import org.itest.config.ITestConfigImpl;
import org.itest.executor.ITestExecutorUtil;
import org.junit.Assert;
import org.junit.Test;

import java.util.List;

public class ITestGenericWithWildcardsTest {

    static class Issue5Class {
        int i;

        @ITests({ @ITest(name = "s1", init = "A:[[{class:org.itest.test.issue.ITestGenericWithWildcardsTest$Issue5SubClass,i:7}]]", verify = "A:[[{i:7}]]"),
                @ITest(name = "s2", init = "A:[[{i:7}]]", verify = "A:[[{i:7}]]") })
        public void shouldWorkWithNoWildcards(List<Issue5Class> a) {

        }

        @ITests({ @ITest(name = "s3", init = "A:[[{class:org.itest.test.issue.ITestGenericWithWildcardsTest$Issue5SubClass,i:7}]]", verify = "A:[[{i:7}]]")
        // ,@ITest(name = "s4", init = "A:[[{i:7}]]", verify = "A:[[{i:7}]]")
        })
        public void shouldWorkWithOnlyWildcard(List<?> a) {

        }

        @ITests({ @ITest(name = "s5", init = "A:[[{class:org.itest.test.issue.ITestGenericWithWildcardsTest$Issue5SubClass,i:7}]]", verify = "A:[[{i:7}]]"),
                @ITest(name = "s6", init = "A:[[{i:7}]]", verify = "A:[[{i:7}]]") })
        public void shouldWorkWithWildcardExtends(List<? extends Issue5Class> a) {

        }

        @ITests({ @ITest(name = "s7", init = "A:[[{class:org.itest.test.issue.ITestGenericWithWildcardsTest$Issue5Class,i:7}]]", verify = "A:[[{i:7}]]"),
                @ITest(name = "s8", init = "A:[[{},{}]]", verify = "A:[[{@class:java.lang.Object}]]")
        })
        public void shouldWorkWithWildcardSuper(List<? super Issue5SubClass> a) {

        }
    }

    static class Issue5SubClass extends Issue5Class {
    }

    @Test
    public void issue5Test() {
        ITestExecutor executor = ITestExecutorUtil.buildExecutor(new ITestConfigImpl());
        Assert.assertThat(executor.performTestsFor(8, Issue5Class.class), CoreMatchers.is(""));
    }

}
