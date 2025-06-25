package com.wmsay.gpt4_lll.component;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.ui.JBSplitter;
import com.intellij.ui.SearchTextField;
import com.intellij.ui.components.JBList;
import com.intellij.ui.components.JBScrollPane;
import com.intellij.util.ui.JBUI;
import com.wmsay.gpt4_lll.JsonStorage;
import com.wmsay.gpt4_lll.model.Message;
import com.wmsay.gpt4_lll.model.key.Gpt4lllChatKey;
import com.wmsay.gpt4_lll.model.key.Gpt4lllTextAreaKey;
import com.wmsay.gpt4_lll.utils.ChatUtils;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.border.TitledBorder;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import java.awt.*;
import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class ChatHistoryManager extends DialogWrapper {
    private final Project project;
    private JBList<HistoryItem> historyList;
    private DefaultListModel<HistoryItem> listModel;
    private JPanel previewPanel;
    private JTextArea previewTextArea;
    private SearchTextField searchField;
    private JComboBox<String> dateFilterComboBox;
    private Map<String, List<Message>> originalHistoryData;
    private final DateTimeFormatter dateFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    public ChatHistoryManager(@Nullable Project project) {
        super(project);
        this.project = project;
        setTitle("Chat History Manager");
        init();
    }

    @Nullable
    @Override
    protected JComponent createCenterPanel() {
        JPanel mainPanel = new JPanel(new BorderLayout());
        mainPanel.setPreferredSize(new Dimension(900, 600));

        // Create filter panel
        JPanel filterPanel = createFilterPanel();
        mainPanel.add(filterPanel, BorderLayout.NORTH);

        // Create splitter for list and preview
        JBSplitter splitter = new JBSplitter(false, 0.3f);

        // Create history list
        createHistoryList();
        JPanel listPanel = new JPanel(new BorderLayout());
        listPanel.add(new JBScrollPane(historyList), BorderLayout.CENTER);

        // Create button panel for list operations
        JPanel listButtonPanel = createListButtonPanel();
        listPanel.add(listButtonPanel, BorderLayout.SOUTH);

        splitter.setFirstComponent(listPanel);

        // Create preview panel
        createPreviewPanel();
        splitter.setSecondComponent(previewPanel);

        mainPanel.add(splitter, BorderLayout.CENTER);

        loadHistoryData();

        return mainPanel;
    }

    private JPanel createFilterPanel() {
        JPanel filterPanel = new JPanel(new GridBagLayout());
        filterPanel.setBorder(BorderFactory.createTitledBorder(
                BorderFactory.createEtchedBorder(), "Filters", TitledBorder.LEFT, TitledBorder.TOP));

        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = JBUI.insets(5);
        gbc.fill = GridBagConstraints.HORIZONTAL;

        // Search field
        gbc.gridx = 0;
        gbc.gridy = 0;
        gbc.weightx = 0.0;
        filterPanel.add(new JLabel("Search:"), gbc);

        searchField = new SearchTextField();
        searchField.getTextEditor().getDocument().addDocumentListener(new DocumentListener() {
            @Override
            public void insertUpdate(DocumentEvent e) {
                applyFilters();
            }

            @Override
            public void removeUpdate(DocumentEvent e) {
                applyFilters();
            }

            @Override
            public void changedUpdate(DocumentEvent e) {
                applyFilters();
            }
        });

        gbc.gridx = 1;
        gbc.weightx = 1.0;
        filterPanel.add(searchField, gbc);

        // Date filter
        gbc.gridx = 2;
        gbc.weightx = 0.0;
        filterPanel.add(new JLabel("Date:"), gbc);

        dateFilterComboBox = new JComboBox<>(new String[] {
                "All Time", "Today", "Yesterday", "Last 7 Days", "Last 30 Days", "This Month", "Last Month"
        });
        dateFilterComboBox.addActionListener(e -> applyFilters());

        gbc.gridx = 3;
        gbc.weightx = 0.5;
        filterPanel.add(dateFilterComboBox, gbc);


        return filterPanel;
    }

    private void createHistoryList() {
        listModel = new DefaultListModel<>();
        historyList = new JBList<>(listModel);
        historyList.setCellRenderer(new DefaultListCellRenderer() {
            @Override
            public Component getListCellRendererComponent(JList<?> list, Object value, int index, boolean isSelected, boolean cellHasFocus) {
                JLabel label = (JLabel) super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
                if (value instanceof HistoryItem) {
                    HistoryItem item = (HistoryItem) value;
                    label.setText(item.getDisplayName());
                    label.setToolTipText(item.getFullTopic());
                }
                return label;
            }
        });

        historyList.addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) {
                HistoryItem selectedItem = historyList.getSelectedValue();
                if (selectedItem != null) {
                    updatePreviewPanel(selectedItem);
                }
            }
        });
    }

    private JPanel createListButtonPanel() {
        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));

        JButton deleteButton = new JButton("Delete");
        deleteButton.addActionListener(e -> deleteSelectedHistory());

        JButton exportButton = new JButton("Export");
        exportButton.addActionListener(e -> exportSelectedHistory());

        JButton renameButton = new JButton("Rename");
        renameButton.addActionListener(e -> renameSelectedHistory());

        buttonPanel.add(deleteButton);
        buttonPanel.add(exportButton);
        buttonPanel.add(renameButton);

        return buttonPanel;
    }

    private void createPreviewPanel() {
        previewPanel = new JPanel(new BorderLayout());
        previewPanel.setBorder(BorderFactory.createTitledBorder(
                BorderFactory.createEtchedBorder(), "Preview", TitledBorder.LEFT, TitledBorder.TOP));

        previewTextArea = new JTextArea();
        previewTextArea.setEditable(false);
        previewTextArea.setLineWrap(true);
        previewTextArea.setWrapStyleWord(true);

        JBScrollPane scrollPane = new JBScrollPane(previewTextArea);
        previewPanel.add(scrollPane, BorderLayout.CENTER);

        JPanel actionPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        JButton loadButton = new JButton("Load This Chat");
        loadButton.addActionListener(e -> loadSelectedChat());
        actionPanel.add(loadButton);

        previewPanel.add(actionPanel, BorderLayout.SOUTH);
    }

    private void loadHistoryData() {
        try {
            originalHistoryData = JsonStorage.loadData();
            populateHistoryList(originalHistoryData);
        } catch (IOException e) {
            JOptionPane.showMessageDialog(getContentPanel(),
                    "Failed to load history data: " + e.getMessage(),
                    "Error",
                    JOptionPane.ERROR_MESSAGE);
        }
    }



    private void populateHistoryList(Map<String, List<Message>> historyData) {
        listModel.clear();

        for (Map.Entry<String, List<Message>> entry : historyData.entrySet()) {
            String topic = entry.getKey();
            HistoryItem item = new HistoryItem(topic, entry.getValue());
            listModel.addElement(item);
        }

        if (listModel.size() > 0) {
            historyList.setSelectedIndex(0);
        }
    }

    private void updatePreviewPanel(HistoryItem item) {
        StringBuilder sb = new StringBuilder();

        for (Message message : item.getMessages()) {
            sb.append(message.getRole().toUpperCase()).append(": ");
            sb.append(message.getContent());
            sb.append("\n\n");
        }

        previewTextArea.setText(sb.toString());
        previewTextArea.setCaretPosition(0);
    }

    private void applyFilters() {
        Map<String, List<Message>> filteredData = new LinkedHashMap<>(originalHistoryData);

        // Apply search filter
        String searchTerm = searchField.getText().toLowerCase().trim();
        if (!searchTerm.isEmpty()) {
            filteredData.entrySet().removeIf(entry ->
                    !entry.getKey().toLowerCase().contains(searchTerm) &&
                            !containsSearchTerm(entry.getValue(), searchTerm));
        }

        // Apply date filter
        String dateFilter = (String) dateFilterComboBox.getSelectedItem();
        if (dateFilter != null && !dateFilter.equals("All Time")) {
            LocalDateTime cutoffDate = getCutoffDate(dateFilter);
            if (cutoffDate != null) {
                filteredData.entrySet().removeIf(entry -> !isWithinDateRange(entry.getKey(), cutoffDate));
            }
        }
        populateHistoryList(filteredData);
    }

    private boolean containsSearchTerm(List<Message> messages, String searchTerm) {
        for (Message message : messages) {
            if (message.getContent() != null && message.getContent().toLowerCase().contains(searchTerm)) {
                return true;
            }
        }
        return false;
    }



    private LocalDateTime getCutoffDate(String dateFilter) {
        LocalDateTime now = LocalDateTime.now();

        switch (dateFilter) {
            case "Today":
                return now.toLocalDate().atStartOfDay();
            case "Yesterday":
                return now.toLocalDate().minusDays(1).atStartOfDay();
            case "Last 7 Days":
                return now.minusDays(7);
            case "Last 30 Days":
                return now.minusDays(30);
            case "This Month":
                return now.withDayOfMonth(1).toLocalDate().atStartOfDay();
            case "Last Month":
                return now.minusMonths(1).withDayOfMonth(1).toLocalDate().atStartOfDay();
            default:
                return null;
        }
    }

    private boolean isWithinDateRange(String topic, LocalDateTime cutoffDate) {
        try {
            // Assuming the topic starts with a date string like "2023-11-29 14:30:22--Chat:..."
            int dateEndIndex = topic.indexOf("--");
            if (dateEndIndex > 0) {
                String dateStr = topic.substring(0, dateEndIndex);
                LocalDateTime topicDate = LocalDateTime.parse(dateStr, dateFormatter);
                return !topicDate.isBefore(cutoffDate);
            }
        } catch (Exception ignored) {
            // If parsing fails, don't filter out this topic
        }
        return true;
    }

    private void deleteSelectedHistory() {
        HistoryItem selectedItem = historyList.getSelectedValue();
        if (selectedItem == null) {
            return;
        }

        int result = JOptionPane.showConfirmDialog(
                getContentPanel(),
                "Are you sure you want to delete this chat history?\n" + selectedItem.getDisplayName(),
                "Confirm Deletion",
                JOptionPane.YES_NO_OPTION
        );

        if (result == JOptionPane.YES_OPTION) {
            try {
                originalHistoryData.remove(selectedItem.getFullTopic());
                JsonStorage.saveData((LinkedHashMap<String, List<Message>>) originalHistoryData);
                applyFilters(); // Refresh the list
                previewTextArea.setText(""); // Clear preview
            } catch (IOException e) {
                JOptionPane.showMessageDialog(
                        getContentPanel(),
                        "Failed to delete history: " + e.getMessage(),
                        "Error",
                        JOptionPane.ERROR_MESSAGE
                );
            }
        }
    }

    private void exportSelectedHistory() {
        HistoryItem selectedItem = historyList.getSelectedValue();
        if (selectedItem == null) {
            return;
        }

        JFileChooser fileChooser = new JFileChooser();
        fileChooser.setDialogTitle("Export Chat History");
        fileChooser.setSelectedFile(new java.io.File(sanitizeFilename(selectedItem.getDisplayName()) + ".txt"));

        int result = fileChooser.showSaveDialog(getContentPanel());
        if (result == JFileChooser.APPROVE_OPTION) {
            try {
                java.io.File file = fileChooser.getSelectedFile();
                try (java.io.PrintWriter writer = new java.io.PrintWriter(file)) {
                    writer.println("Chat: " + selectedItem.getFullTopic());
                    writer.println("Date: " + selectedItem.getFullTopic().split("--")[0]);
                    writer.println("----------------------------------------");

                    for (Message message : selectedItem.getMessages()) {
                        writer.println(message.getRole().toUpperCase() + ": " + message.getContent());
                        writer.println("----------------------------------------");
                    }
                }

                JOptionPane.showMessageDialog(
                        getContentPanel(),
                        "History exported successfully!",
                        "Export Complete",
                        JOptionPane.INFORMATION_MESSAGE
                );
            } catch (IOException e) {
                JOptionPane.showMessageDialog(
                        getContentPanel(),
                        "Failed to export history: " + e.getMessage(),
                        "Error",
                        JOptionPane.ERROR_MESSAGE
                );
            }
        }
    }

    private String sanitizeFilename(String input) {
        return input.replaceAll("[\\\\/:*?\"<>|]", "_");
    }

    private void renameSelectedHistory() {
        HistoryItem selectedItem = historyList.getSelectedValue();
        if (selectedItem == null) {
            return;
        }

        String newName = JOptionPane.showInputDialog(
                getContentPanel(),
                "Enter new name for this chat history:",
                selectedItem.getDisplayName()
        );

        if (newName != null && !newName.trim().isEmpty()) {
            try {
                // Create a new topic with the same date but new name
                String oldTopic = selectedItem.getFullTopic();
                String datePart = oldTopic.split("--")[0];
                String newTopic = datePart + "--Chat:" + newName;

                // Get the messages and update the map
                List<Message> messages = originalHistoryData.remove(oldTopic);
                originalHistoryData.put(newTopic, messages);

                // Save and refresh
                JsonStorage.saveData((LinkedHashMap<String, List<Message>>) originalHistoryData);
                applyFilters();
            } catch (IOException e) {
                JOptionPane.showMessageDialog(
                        getContentPanel(),
                        "Failed to rename history: " + e.getMessage(),
                        "Error",
                        JOptionPane.ERROR_MESSAGE
                );
            }
        }
    }

    private void loadSelectedChat() {
        HistoryItem selectedItem = historyList.getSelectedValue();
        if (selectedItem == null) {
            return;
        }

        // Load the selected chat into the current project
        String topic = selectedItem.getFullTopic();
        List<Message> messages = selectedItem.getMessages();

        // Update project data
        ChatUtils.setProjectTopic(project, topic);
        project.putUserData(Gpt4lllChatKey.GPT_4_LLL_CONVERSATION_HISTORY, new ArrayList<>(messages));

        // Update the chat window
        Gpt4lllTextArea textArea = project.getUserData(Gpt4lllTextAreaKey.GPT_4_LLL_TEXT_AREA);
        if (textArea != null) {
            textArea.clearShowWindow();
            for (Message message : messages) {
                textArea.appendMessage(message);
            }
        }

        close(OK_EXIT_CODE);
    }

    @Override
    protected @Nullable JComponent createSouthPanel() {
        // Return null to remove the default OK/Cancel buttons
        return null;
    }

    // Inner class to represent a history item
    static class HistoryItem {
        private final String fullTopic;
        private final List<Message> messages;

        public HistoryItem(String fullTopic, List<Message> messages) {
            this.fullTopic = fullTopic;
            this.messages = messages;
        }

        public String getFullTopic() {
            return fullTopic;
        }

        public String getDisplayName() {
            // Extract the chat name from the full topic (after "--Chat:")
            int chatIndex = fullTopic.indexOf("--Chat:");
            if (chatIndex >= 0 && chatIndex + 7 < fullTopic.length()) {
                return fullTopic.substring(chatIndex + 7);
            }
            return fullTopic;
        }

        public List<Message> getMessages() {
            return messages;
        }
    }
}
