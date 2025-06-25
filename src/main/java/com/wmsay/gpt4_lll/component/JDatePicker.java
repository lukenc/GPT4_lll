package com.wmsay.gpt4_lll.component;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionListener;
import java.time.LocalDate;

/**
 * A simple date picker component for the Work Report dialog
 */
public class JDatePicker extends JPanel {
    private final JComboBox<Integer> dayCombo;
    private final JComboBox<String> monthCombo;
    private final JComboBox<Integer> yearCombo;
    private LocalDate currentDate;

    public JDatePicker() {
        super(new FlowLayout(FlowLayout.LEFT, 2, 0));

        // Initialize with current date
        currentDate = LocalDate.now();

        // Day combo box (1-31)
        Integer[] days = new Integer[31];
        for (int i = 0; i < 31; i++) {
            days[i] = i + 1;
        }
        dayCombo = new JComboBox<>(days);
        dayCombo.setSelectedItem(currentDate.getDayOfMonth());

        // Month combo box
        String[] months = {"January", "February", "March", "April", "May", "June",
                "July", "August", "September", "October", "November", "December"};
        monthCombo = new JComboBox<>(months);
        monthCombo.setSelectedIndex(currentDate.getMonthValue() - 1);

        // Year combo box (current year - 5 to current year)
        int currentYear = currentDate.getYear();
        Integer[] years = new Integer[6];
        for (int i = 0; i < years.length; i++) {
            years[i] = currentYear - 5 + i;
        }
        yearCombo = new JComboBox<>(years);
        yearCombo.setSelectedItem(currentYear);

        // Add components
        add(dayCombo);
        add(monthCombo);
        add(yearCombo);

        // Add listeners to update the date when selections change
        ActionListener updateListener = e -> updateDateFromComponents();
        dayCombo.addActionListener(updateListener);
        monthCombo.addActionListener(updateListener);
        yearCombo.addActionListener(updateListener);
    }

    /**
     * Updates the current date based on the selected components
     */
    private void updateDateFromComponents() {
        int day = (Integer) dayCombo.getSelectedItem();
        int month = monthCombo.getSelectedIndex() + 1;
        int year = (Integer) yearCombo.getSelectedItem();

        // Validate the day for the selected month
        int maxDays = getMaxDaysInMonth(month, year);
        if (day > maxDays) {
            day = maxDays;
            dayCombo.setSelectedItem(day);
        }

        try {
            currentDate = LocalDate.of(year, month, day);
        } catch (Exception e) {
            // Fallback to current date if invalid
            currentDate = LocalDate.now();
            updateComponentsFromDate();
        }
    }

    /**
     * Updates the component selections based on the current date
     */
    private void updateComponentsFromDate() {
        dayCombo.setSelectedItem(currentDate.getDayOfMonth());
        monthCombo.setSelectedIndex(currentDate.getMonthValue() - 1);
        yearCombo.setSelectedItem(currentDate.getYear());
    }

    /**
     * Gets the maximum number of days in the specified month and year
     */
    private int getMaxDaysInMonth(int month, int year) {
        return LocalDate.of(year, month, 1).lengthOfMonth();
    }

    /**
     * Gets the currently selected date
     */
    public LocalDate getDate() {
        return currentDate;
    }

    /**
     * Sets the current date
     */
    public void setDate(LocalDate date) {
        currentDate = date;
        updateComponentsFromDate();
    }

    /**
     * Enables or disables this component
     */
    @Override
    public void setEnabled(boolean enabled) {
        super.setEnabled(enabled);
        dayCombo.setEnabled(enabled);
        monthCombo.setEnabled(enabled);
        yearCombo.setEnabled(enabled);
    }
}
