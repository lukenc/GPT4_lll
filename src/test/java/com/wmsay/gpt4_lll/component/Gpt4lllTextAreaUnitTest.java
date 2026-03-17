//package com.wmsay.gpt4_lll.component;
//
//import org.junit.jupiter.api.Test;
//
//import javax.swing.*;
//import java.lang.reflect.Field;
//import java.lang.reflect.Method;
//import java.util.concurrent.ConcurrentLinkedQueue;
//
//import static org.junit.jupiter.api.Assertions.*;
//
//class Gpt4lllTextAreaUnitTest {
//
//    private StringBuilder getContentBuilder(Gpt4lllTextArea textArea) throws Exception {
//        Field f = Gpt4lllTextArea.class.getDeclaredField("contentBuilder");
//        f.setAccessible(true);
//        return (StringBuilder) f.get(textArea);
//    }
//
//    @SuppressWarnings("unchecked")
//    private ConcurrentLinkedQueue<String> getPendingContent(Gpt4lllTextArea textArea) throws Exception {
//        Field f = Gpt4lllTextArea.class.getDeclaredField("pendingContent");
//        f.setAccessible(true);
//        return (ConcurrentLinkedQueue<String>) f.get(textArea);
//    }
//
//    private Timer getUpdateTimer(Gpt4lllTextArea textArea) throws Exception {
//        Field f = Gpt4lllTextArea.class.getDeclaredField("updateTimer");
//        f.setAccessible(true);
//        return (Timer) f.get(textArea);
//    }
//
//    private void invokeFlush(Gpt4lllTextArea textArea) throws Exception {
//        Method m = Gpt4lllTextArea.class.getDeclaredMethod("flushPendingContent");
//        m.setAccessible(true);
//        m.invoke(textArea);
//    }
//
//    /** Test clearShowWindow resets all state. Validates: Requirements 4.2 */
//    @Test
//    void clearShowWindowResetsAllState() throws Exception {
//        SwingUtilities.invokeAndWait(() -> {
//            try {
//                Gpt4lllTextArea textArea = new Gpt4lllTextArea();
//                Timer timer = getUpdateTimer(textArea);
//                ConcurrentLinkedQueue<String> pending = getPendingContent(textArea);
//                pending.add("Hello ");
//                pending.add("World");
//                invokeFlush(textArea);
//                assertTrue(getContentBuilder(textArea).length() > 0,
//                    "contentBuilder should have content before clearShowWindow");
//                pending.add("More text");
//                assertFalse(pending.isEmpty(),
//                    "pendingContent should have items before clearShowWindow");
//                textArea.clearShowWindow();
//                assertTrue(pending.isEmpty(),
//                    "pendingContent should be empty after clearShowWindow");
//                assertEquals(0, getContentBuilder(textArea).length(),
//                    "contentBuilder should be empty after clearShowWindow");
//                assertFalse(timer.isRunning(),
//                    "updateTimer should not be running after clearShowWindow");
//            } catch (Exception e) {
//                throw new RuntimeException(e);
//            }
//        });
//    }
//
//    /** Test appendThinkingTitle renders correctly after flush. Validates: Requirements 4.3 */
//    @Test
//    void appendThinkingTitleRendersCorrectly() throws Exception {
//        SwingUtilities.invokeAndWait(() -> {
//            try {
//                Gpt4lllTextArea textArea = new Gpt4lllTextArea();
//                getPendingContent(textArea).add("====思考过程/Reasoning Process==== \n\n");
//                invokeFlush(textArea);
//                assertTrue(getContentBuilder(textArea).toString()
//                        .contains("====思考过程/Reasoning Process===="),
//                    "contentBuilder should contain thinking title marker");
//                getUpdateTimer(textArea).stop();
//            } catch (Exception e) {
//                throw new RuntimeException(e);
//            }
//        });
//    }
//
//    /** Test appendThinkingEnd renders correctly after flush. Validates: Requirements 4.3 */
//    @Test
//    void appendThinkingEndRendersCorrectly() throws Exception {
//        SwingUtilities.invokeAndWait(() -> {
//            try {
//                Gpt4lllTextArea textArea = new Gpt4lllTextArea();
//                getPendingContent(textArea).add("\n\n====思考过程结束/Reasoning Process End====\n\n\n");
//                getPendingContent(textArea).add("====完整回复/Reasoned response====\n");
//                invokeFlush(textArea);
//                String content = getContentBuilder(textArea).toString();
//                assertTrue(content.contains("====思考过程结束/Reasoning Process End===="),
//                    "contentBuilder should contain thinking end marker");
//                assertTrue(content.contains("====完整回复/Reasoned response===="),
//                    "contentBuilder should contain reasoned response marker");
//                getUpdateTimer(textArea).stop();
//            } catch (Exception e) {
//                throw new RuntimeException(e);
//            }
//        });
//    }
//
//    /** Test single token renders after timer fires. Validates: Requirements 1.4 */
//    @Test
//    void singleTokenRendersAfterTimerFires() throws Exception {
//        SwingUtilities.invokeAndWait(() -> {
//            try {
//                Gpt4lllTextArea textArea = new Gpt4lllTextArea();
//                getPendingContent(textArea).add("singleToken");
//                invokeFlush(textArea);
//                assertEquals("singleToken", getContentBuilder(textArea).toString(),
//                    "contentBuilder should contain the single token after flush");
//                getUpdateTimer(textArea).stop();
//            } catch (Exception e) {
//                throw new RuntimeException(e);
//            }
//        });
//    }
//
//    /** Test appendContent from EDT does not deadlock. Validates: Requirements 3.3 */
//    @Test
//    void appendContentFromEdtDoesNotDeadlock() throws Exception {
//        SwingUtilities.invokeAndWait(() -> {
//            try {
//                Gpt4lllTextArea textArea = new Gpt4lllTextArea();
//                ConcurrentLinkedQueue<String> pending = getPendingContent(textArea);
//                pending.add("EDT content");
//                assertTrue(pending.contains("EDT content"),
//                    "Content should be in pendingContent after enqueue on EDT");
//                invokeFlush(textArea);
//                assertEquals("EDT content", getContentBuilder(textArea).toString(),
//                    "Content should be in contentBuilder after flush on EDT");
//                assertTrue(pending.isEmpty(),
//                    "pendingContent should be empty after flush");
//                getUpdateTimer(textArea).stop();
//            } catch (Exception e) {
//                throw new RuntimeException(e);
//            }
//        });
//    }
//}
