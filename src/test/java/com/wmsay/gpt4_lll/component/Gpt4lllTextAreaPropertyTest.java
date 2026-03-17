//package com.wmsay.gpt4_lll.component;
//
//import net.jqwik.api.*;
//import javax.swing.*;
//import java.awt.Point;
//import java.lang.reflect.Field;
//import java.lang.reflect.Method;
//import java.util.List;
//
//class Gpt4lllTextAreaPropertyTest {
//
//    /**
//     * Property 2: Content completeness after flush
//     * Validates: Requirements 1.2
//     *
//     * For any sequence of tokens appended via appendContent, after flushPendingContent
//     * completes, the contentBuilder contains all tokens concatenated in order.
//     */
//    @Property(tries = 100)
//    void contentCompletenessAfterFlush(@ForAll List<@From("tokens") String> tokens) throws Exception {
//        SwingUtilities.invokeAndWait(() -> {
//            try {
//                Gpt4lllTextArea textArea = new Gpt4lllTextArea();
//
//                for (String token : tokens) {
//                    textArea.appendContent(token);
//                }
//
//                // Force flush via reflection
//                Method flush = Gpt4lllTextArea.class.getDeclaredMethod("flushPendingContent");
//                flush.setAccessible(true);
//                flush.invoke(textArea);
//
//                // Read contentBuilder via reflection
//                Field cbField = Gpt4lllTextArea.class.getDeclaredField("contentBuilder");
//                cbField.setAccessible(true);
//                StringBuilder cb = (StringBuilder) cbField.get(textArea);
//
//                String expected = String.join("", tokens);
//                assert cb.toString().equals(expected) :
//                    "Expected contentBuilder to be '" + expected + "' but was '" + cb.toString() + "'";
//            } catch (Exception e) {
//                throw new RuntimeException(e);
//            }
//        });
//    }
//
//    @Provide
//    Arbitrary<String> tokens() {
//        return Arbitraries.strings().alpha().ofMinLength(1).ofMaxLength(20);
//    }
//
//
//    /**
//     * Property 3: Scroll-to-bottom preservation
//     * Validates: Requirements 2.1, 2.4
//     *
//     * For any content update where the scroll position was at the bottom before
//     * the update, after flushPendingContent completes, the scroll position shall
//     * be at the bottom of the new content.
//     */
//    @Property(tries = 100)
//    void scrollToBottomPreservation(@ForAll List<@From("tokens") String> tokens) throws Exception {
//        SwingUtilities.invokeAndWait(() -> {
//            JFrame frame = null;
//            try {
//                frame = new JFrame();
//                Gpt4lllTextArea textArea = new Gpt4lllTextArea();
//                JScrollPane scrollPane = new JScrollPane(textArea);
//                textArea.setScrollPane(scrollPane);
//                frame.add(scrollPane);
//                frame.setSize(400, 200);
//                frame.setVisible(true);
//
//                Method flush = Gpt4lllTextArea.class.getDeclaredMethod("flushPendingContent");
//                flush.setAccessible(true);
//                Method isAtBottom = Gpt4lllTextArea.class.getDeclaredMethod("isAtBottom");
//                isAtBottom.setAccessible(true);
//                Method scrollToBottom = Gpt4lllTextArea.class.getDeclaredMethod("scrollToBottom");
//                scrollToBottom.setAccessible(true);
//
//                // Add initial content to make it scrollable (overflow the viewport)
//                for (int i = 0; i < 50; i++) {
//                    textArea.appendContent("Initial line " + i + "\n");
//                }
//                flush.invoke(textArea);
//
//                // Scroll to bottom and let layout settle
//                scrollToBottom.invoke(textArea);
//                // Force layout revalidation so scroll positions are computed
//                scrollPane.getViewport().validate();
//                textArea.revalidate();
//                scrollPane.revalidate();
//
//                // Verify we are at bottom before appending new content
//                boolean atBottomBefore = (boolean) isAtBottom.invoke(textArea);
//                // If layout hasn't settled enough for scroll to register, skip this iteration
//                if (!atBottomBefore) {
//                    return;
//                }
//
//                // Now append more random content
//                for (String token : tokens) {
//                    textArea.appendContent(token + "\n");
//                }
//                flush.invoke(textArea);
//
//                // flushPendingContent should have detected wasAtBottom and called scrollToBottom
//                // Force layout again
//                scrollPane.getViewport().validate();
//                textArea.revalidate();
//                scrollPane.revalidate();
//
//                boolean atBottomAfter = (boolean) isAtBottom.invoke(textArea);
//                assert atBottomAfter : "Expected scroll to be at bottom after flush when it was at bottom before";
//            } catch (Exception e) {
//                throw new RuntimeException(e);
//            } finally {
//                if (frame != null) {
//                    frame.dispose();
//                }
//            }
//        });
//    }
//
//
//    /**
//     * Property 4: Scroll position preservation when not at bottom
//     * Validates: Requirements 2.2
//     *
//     * For any content update where the user's viewport position was above the bottom,
//     * after flushPendingContent completes, the viewport position shall be equal to
//     * the position before the update.
//     */
//    @Property(tries = 100)
//    void scrollPositionPreservationWhenNotAtBottom(@ForAll List<@From("tokens") String> tokens) throws Exception {
//        SwingUtilities.invokeAndWait(() -> {
//            JFrame frame = null;
//            try {
//                frame = new JFrame();
//                Gpt4lllTextArea textArea = new Gpt4lllTextArea();
//                JScrollPane scrollPane = new JScrollPane(textArea);
//                textArea.setScrollPane(scrollPane);
//                frame.add(scrollPane);
//                frame.setSize(400, 200);
//                frame.setVisible(true);
//
//                Method flush = Gpt4lllTextArea.class.getDeclaredMethod("flushPendingContent");
//                flush.setAccessible(true);
//                Method isAtBottom = Gpt4lllTextArea.class.getDeclaredMethod("isAtBottom");
//                isAtBottom.setAccessible(true);
//
//                // Add enough initial content to make it scrollable (50+ lines)
//                for (int i = 0; i < 60; i++) {
//                    textArea.appendContent("Initial line " + i + " with some extra text to fill width\n");
//                }
//                flush.invoke(textArea);
//
//                // Force layout so scroll positions are computed
//                scrollPane.getViewport().validate();
//                textArea.revalidate();
//                scrollPane.revalidate();
//
//                // Set viewport to top (position 0,0) — definitely not at bottom
//                scrollPane.getViewport().setViewPosition(new Point(0, 0));
//                scrollPane.getViewport().validate();
//                textArea.revalidate();
//                scrollPane.revalidate();
//
//                // Verify we are NOT at bottom
//                boolean atBottomBefore = (boolean) isAtBottom.invoke(textArea);
//                if (atBottomBefore) {
//                    // Content wasn't enough to make it scrollable, skip this iteration
//                    return;
//                }
//
//                // Capture viewport position before appending new content
//                Point positionBefore = scrollPane.getViewport().getViewPosition();
//
//                // Append more random content
//                for (String token : tokens) {
//                    textArea.appendContent(token + "\n");
//                }
//                flush.invoke(textArea);
//
//                // Force layout again
//                scrollPane.getViewport().validate();
//                textArea.revalidate();
//                scrollPane.revalidate();
//
//                // Verify viewport position is preserved
//                Point positionAfter = scrollPane.getViewport().getViewPosition();
//                assert positionBefore.equals(positionAfter) :
//                    "Expected viewport position " + positionBefore + " but was " + positionAfter;
//            } catch (Exception e) {
//                throw new RuntimeException(e);
//            } finally {
//                if (frame != null) {
//                    frame.dispose();
//                }
//            }
//        });
//    }
//
//    /**
//     * Property 1: Batching reduces render count
//     * Validates: Requirements 1.1, 1.3
//     *
//     * For any sequence of N (N > 1) rapid appendContent calls that arrive within
//     * a single timer interval, the number of actual HTML re-renders (setText calls)
//     * shall be strictly less than N.
//     */
//    @Property(tries = 100)
//    void batchingReducesRenderCount(@ForAll("multipleTokens") List<String> tokens) throws Exception {
//        SwingUtilities.invokeAndWait(() -> {
//            try {
//                // Track setText calls using an array (effectively final for lambda)
//                int[] setTextCount = {0};
//                Gpt4lllTextArea textArea = new Gpt4lllTextArea() {
//                    @Override
//                    public void setText(String t) {
//                        setTextCount[0]++;
//                        super.setText(t);
//                    }
//                };
//
//                // Reset counter after constructor (constructor may call setText indirectly)
//                setTextCount[0] = 0;
//
//                int n = tokens.size();
//
//                // Call appendContent rapidly — all within same EDT frame, no timer fires
//                for (String token : tokens) {
//                    textArea.appendContent(token);
//                }
//
//                // Force a single flush
//                Method flush = Gpt4lllTextArea.class.getDeclaredMethod("flushPendingContent");
//                flush.setAccessible(true);
//                flush.invoke(textArea);
//
//                assert setTextCount[0] < n :
//                    "Expected fewer than " + n + " setText calls but got " + setTextCount[0];
//            } catch (Exception e) {
//                throw new RuntimeException(e);
//            }
//        });
//    }
//
//    @Provide
//    Arbitrary<List<String>> multipleTokens() {
//        return Arbitraries.strings().alpha().ofMinLength(1).ofMaxLength(20)
//            .list().ofMinSize(2).ofMaxSize(50);
//    }
//
//    /**
//     * Property 5: EDT rendering guarantee
//     * Validates: Requirements 3.1, 3.2
//     *
//     * For any appendContent call originating from a non-EDT thread,
//     * the actual setText call on the JEditorPane shall execute on the EDT.
//     */
//    @Property(tries = 100)
//    void edtRenderingGuarantee(@ForAll("multipleTokens") List<String> tokens) throws Exception {
//        // Track whether setText was called on EDT
//        boolean[] allOnEdt = {true};
//
//        // Must create the textArea on EDT
//        Gpt4lllTextArea[] textAreaHolder = new Gpt4lllTextArea[1];
//        SwingUtilities.invokeAndWait(() -> {
//            textAreaHolder[0] = new Gpt4lllTextArea() {
//                @Override
//                public void setText(String t) {
//                    if (!SwingUtilities.isEventDispatchThread()) {
//                        allOnEdt[0] = false;
//                    }
//                    super.setText(t);
//                }
//            };
//        });
//
//        // Reset flag after constructor (constructor may call setText)
//        allOnEdt[0] = true;
//
//        // Call appendContent from a non-EDT thread
//        Thread bgThread = new Thread(() -> {
//            for (String token : tokens) {
//                textAreaHolder[0].appendContent(token);
//            }
//        });
//        bgThread.start();
//        bgThread.join();
//
//        // Wait for timer to fire and flush (COALESCE_DELAY_MS = 80ms, wait a bit more)
//        Thread.sleep(200);
//
//        // Process any remaining EDT events
//        SwingUtilities.invokeAndWait(() -> {});
//
//        assert allOnEdt[0] : "setText was called from a non-EDT thread";
//    }
//
//
//
//
//}
