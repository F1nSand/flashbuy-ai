package com.goodskill.web.util;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.UUID;

public class TaskTimeCaculateUtilTest {

    @Test
    void testPrettyPrint() {
        String taskId = UUID.randomUUID().toString();
        TaskTimeCaculateUtil.startTask("Task 1", taskId);
        // Perform some task-related operations
        // ...
        TaskTimeCaculateUtil.stop(taskId);
        String output = TaskTimeCaculateUtil.prettyPrint(taskId);
        Assertions.assertNotNull(output);
    }

    @Test
    void completedTaskMustNotChangeWhenAnotherTaskRuns() {
        String first = UUID.randomUUID().toString();
        String second = UUID.randomUUID().toString();
        TaskTimeCaculateUtil.startTask("first", first);
        TaskTimeCaculateUtil.stop(first);
        Map<String, Object> snapshot = TaskTimeCaculateUtil.getTaskDetails(first);

        TaskTimeCaculateUtil.startTask("second", second);
        TaskTimeCaculateUtil.stop(second);

        Assertions.assertEquals(snapshot, TaskTimeCaculateUtil.getTaskDetails(first));
        Assertions.assertEquals(1, TaskTimeCaculateUtil.getTaskDetails(second).get("taskCount"));
    }

    @Test
    void unknownTaskReturnsNullAndStopIsSafe() {
        String missing = UUID.randomUUID().toString();

        Assertions.assertDoesNotThrow(() -> TaskTimeCaculateUtil.stop(missing));
        Assertions.assertNull(TaskTimeCaculateUtil.getTaskDetails(missing));
    }

    @Test
    void repeatedStopDoesNotChangeCompletedTask() {
        String taskId = UUID.randomUUID().toString();
        TaskTimeCaculateUtil.startTask("repeat stop", taskId);
        TaskTimeCaculateUtil.stop(taskId);
        Map<String, Object> snapshot = TaskTimeCaculateUtil.getTaskDetails(taskId);

        Assertions.assertDoesNotThrow(() -> TaskTimeCaculateUtil.stop(taskId));
        Assertions.assertEquals(snapshot, TaskTimeCaculateUtil.getTaskDetails(taskId));
    }

    @Test
    void duplicateTaskIdDoesNotReplaceCompletedTask() {
        String taskId = UUID.randomUUID().toString();
        TaskTimeCaculateUtil.startTask("original", taskId);
        TaskTimeCaculateUtil.stop(taskId);
        Map<String, Object> snapshot = TaskTimeCaculateUtil.getTaskDetails(taskId);

        TaskTimeCaculateUtil.startTask("replacement", taskId);
        TaskTimeCaculateUtil.stop(taskId);

        Assertions.assertEquals(snapshot, TaskTimeCaculateUtil.getTaskDetails(taskId));
    }

    @Test
    void simultaneouslyRunningTasksRemainIndependent() {
        String first = UUID.randomUUID().toString();
        String second = UUID.randomUUID().toString();
        TaskTimeCaculateUtil.startTask("concurrent first", first);
        TaskTimeCaculateUtil.startTask("concurrent second", second);

        Assertions.assertTrue((Boolean) TaskTimeCaculateUtil.getTaskDetails(first).get("isRunning"));
        Assertions.assertTrue((Boolean) TaskTimeCaculateUtil.getTaskDetails(second).get("isRunning"));

        TaskTimeCaculateUtil.stop(first);
        Assertions.assertFalse((Boolean) TaskTimeCaculateUtil.getTaskDetails(first).get("isRunning"));
        Assertions.assertTrue((Boolean) TaskTimeCaculateUtil.getTaskDetails(second).get("isRunning"));
        TaskTimeCaculateUtil.stop(second);

        Assertions.assertEquals(1, TaskTimeCaculateUtil.getTaskDetails(first).get("taskCount"));
        Assertions.assertEquals(1, TaskTimeCaculateUtil.getTaskDetails(second).get("taskCount"));
    }
}
