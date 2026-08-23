package com.subpulse.service;

import com.lowagie.text.*;
import com.lowagie.text.Font;
import com.lowagie.text.pdf.*;
import com.subpulse.dto.response.AiOptimizationResponse;
import com.subpulse.dto.response.AiRecommendationDto;
import com.subpulse.dto.response.AnalyticsResponse;
import com.subpulse.dto.response.SubscriptionResponse;
import com.subpulse.entity.User;
import com.subpulse.exception.ResourceNotFoundException;
import com.subpulse.repository.UserRepository;
import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * Service for generating executive PDF monthly digests
 * and dispatching rich HTML monthly email summaries.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MonthlyDigestReportService {

    private final UserRepository          userRepository;
    private final SubscriptionService     subscriptionService;
    private final AiOptimizationService   aiOptimizationService;
    private final CurrencyConversionService currencyConversionService;
    private final JavaMailSender          mailSender;

    // Brand Palette
    private static final Color COLOR_PRIMARY     = new Color(99, 102, 241);  // #6366f1 Indigo
    private static final Color COLOR_DARK_BG     = new Color(15, 23, 42);    // #0f172a Slate 900
    private static final Color COLOR_HEADER_BG   = new Color(30, 41, 59);    // #1e293b Slate 800
    private static final Color COLOR_CARD_BG     = new Color(248, 250, 252); // #f8fafc Slate 50
    private static final Color COLOR_TEXT_DARK   = new Color(30, 41, 59);
    private static final Color COLOR_TEXT_MUTED  = new Color(100, 116, 139);
    private static final Color COLOR_SUCCESS     = new Color(16, 185, 129);  // #10b981 Emerald
    private static final Color COLOR_BORDER      = new Color(226, 232, 240);

    @Transactional(readOnly = true)
    public byte[] generatePdfReport(Long userId, String targetCurrency) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User", userId));

        String currency = (targetCurrency != null && !targetCurrency.isBlank())
                ? targetCurrency.toUpperCase()
                : (user.getPreferredCurrency() != null ? user.getPreferredCurrency() : "USD");

        AnalyticsResponse analytics = subscriptionService.getAnalytics(userId, currency);
        List<SubscriptionResponse> subscriptions = subscriptionService.getAllByUser(userId);
        AiOptimizationResponse aiOptimization = aiOptimizationService.analyzeSubscriptions(userId, currency);

        return buildPdf(user, analytics, subscriptions, aiOptimization, currency);
    }

    @Transactional(readOnly = true)
    public void sendMonthlyDigestEmail(Long userId, String targetCurrency) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User", userId));

        String currency = (targetCurrency != null && !targetCurrency.isBlank())
                ? targetCurrency.toUpperCase()
                : (user.getPreferredCurrency() != null ? user.getPreferredCurrency() : "USD");

        AnalyticsResponse analytics = subscriptionService.getAnalytics(userId, currency);
        AiOptimizationResponse aiOptimization = aiOptimizationService.analyzeSubscriptions(userId, currency);
        byte[] pdfBytes = generatePdfReport(userId, currency);

        String monthYear = LocalDate.now().format(DateTimeFormatter.ofPattern("MMMM yyyy"));
        String subject = "📊 SubPulse Monthly Executive Digest — " + monthYear;

        try {
            MimeMessage mimeMessage = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(mimeMessage, true, "UTF-8");

            helper.setTo(user.getEmail());
            helper.setSubject(subject);

            String htmlBody = buildHtmlEmailBody(user, analytics, aiOptimization, currency, monthYear);
            helper.setText(htmlBody, true);

            String fileName = "SubPulse_Monthly_Digest_" + LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy_MM")) + ".pdf";
            helper.addAttachment(fileName, new ByteArrayResource(pdfBytes), "application/pdf");

            mailSender.send(mimeMessage);
            log.info("Monthly executive digest email with PDF attached successfully sent to {}", user.getEmail());
        } catch (Exception e) {
            log.error("Failed to send monthly digest email to {}: {}", user.getEmail(), e.getMessage(), e);
            throw new RuntimeException("Failed to send monthly digest email: " + e.getMessage(), e);
        }
    }

    // ── PDF Builder ─────────────────────────────────────────────────────────────

    private byte[] buildPdf(User user, AnalyticsResponse analytics,
                            List<SubscriptionResponse> subscriptions,
                            AiOptimizationResponse aiOptimization, String currency) {

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        Document document = new Document(PageSize.A4, 36, 36, 36, 36);

        try {
            PdfWriter writer = PdfWriter.getInstance(document, out);
            document.open();

            // Fonts
            Font fontTitle = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 20, Color.WHITE);
            Font fontSubtitle = FontFactory.getFont(FontFactory.HELVETICA, 10, new Color(203, 213, 225));
            Font fontSectionHeader = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 13, COLOR_DARK_BG);
            Font fontCardLabel = FontFactory.getFont(FontFactory.HELVETICA, 9, COLOR_TEXT_MUTED);
            Font fontCardValue = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 15, COLOR_TEXT_DARK);
            Font fontTableHead = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 9, Color.WHITE);
            Font fontTableCell = FontFactory.getFont(FontFactory.HELVETICA, 9, COLOR_TEXT_DARK);
            Font fontAiHeader = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 11, COLOR_PRIMARY);
            Font fontAiText = FontFactory.getFont(FontFactory.HELVETICA, 9, COLOR_TEXT_DARK);

            // 1. Header Banner
            PdfPTable headerTable = new PdfPTable(1);
            headerTable.setWidthPercentage(100);
            PdfPCell headerCell = new PdfPCell();
            headerCell.setBackgroundColor(COLOR_DARK_BG);
            headerCell.setPadding(18);
            headerCell.setBorder(Rectangle.NO_BORDER);

            Paragraph pTitle = new Paragraph("⚡ SUBPULSE — EXECUTIVE MONTHLY REPORT", fontTitle);
            pTitle.setSpacingAfter(4);
            headerCell.addElement(pTitle);

            String monthStr = LocalDate.now().format(DateTimeFormatter.ofPattern("MMMM yyyy"));
            Paragraph pSub = new Paragraph("Generated for " + user.getFullName() + " (" + user.getEmail() + ") • " + monthStr, fontSubtitle);
            headerCell.addElement(pSub);

            headerTable.addCell(headerCell);
            document.add(headerTable);

            document.add(new Paragraph(" "));

            // 2. Executive KPI Cards (4 Column Grid)
            PdfPTable kpiTable = new PdfPTable(4);
            kpiTable.setWidthPercentage(100);
            kpiTable.setSpacingBefore(10);
            kpiTable.setSpacingAfter(15);
            kpiTable.setWidths(new float[]{1, 1, 1, 1});

            addKpiCell(kpiTable, "MONTHLY SPEND", currency + " " + analytics.getMonthlySpend().setScale(2).toPlainString(), fontCardLabel, fontCardValue);
            addKpiCell(kpiTable, "PROJECTED ANNUAL", currency + " " + analytics.getAnnualSpend().setScale(2).toPlainString(), fontCardLabel, fontCardValue);
            addKpiCell(kpiTable, "ACTIVE SERVICES", String.valueOf(analytics.getTotalActiveSubscriptions()), fontCardLabel, fontCardValue);
            addKpiCell(kpiTable, "AI OPTIMIZATION SCORE", aiOptimization.getHealthScore() + " / 100", fontCardLabel, fontCardValue);

            document.add(kpiTable);

            // 3. Subscriptions Breakdown Table
            Paragraph sec1 = new Paragraph("📋 Monitored Subscriptions Breakdown", fontSectionHeader);
            sec1.setSpacingBefore(10);
            sec1.setSpacingAfter(8);
            document.add(sec1);

            PdfPTable table = new PdfPTable(5);
            table.setWidthPercentage(100);
            table.setWidths(new float[]{3.2f, 2.0f, 2.0f, 2.2f, 2.0f});

            // Table Headers
            String[] headers = {"SERVICE", "CATEGORY", "BILLING CYCLE", "NEXT RENEWAL", "COST (" + currency + ")"};
            for (String h : headers) {
                PdfPCell th = new PdfPCell(new Phrase(h, fontTableHead));
                th.setBackgroundColor(COLOR_HEADER_BG);
                th.setPadding(7);
                th.setBorderColor(COLOR_BORDER);
                table.addCell(th);
            }

            // Table Rows
            boolean alternate = false;
            for (SubscriptionResponse s : subscriptions) {
                Color rowBg = alternate ? new Color(241, 245, 249) : Color.WHITE;
                alternate = !alternate;

                BigDecimal convertedAmount = currencyConversionService.convert(s.getAmount(), s.getCurrency(), currency);

                addTableCell(table, s.getServiceName(), fontTableCell, rowBg);
                addTableCell(table, s.getCategory() != null ? s.getCategory().name() : "OTHER", fontTableCell, rowBg);
                addTableCell(table, s.getBillingCycle().name(), fontTableCell, rowBg);
                addTableCell(table, s.getNextBillingDate().toString(), fontTableCell, rowBg);
                addTableCell(table, currency + " " + convertedAmount.setScale(2).toPlainString(), fontTableCell, rowBg);
            }
            document.add(table);

            // 4. AI Cost Optimization Insights
            if (aiOptimization.getRecommendations() != null && !aiOptimization.getRecommendations().isEmpty()) {
                Paragraph sec2 = new Paragraph("🤖 AI Cost Optimization & Savings Insights", fontSectionHeader);
                sec2.setSpacingBefore(16);
                sec2.setSpacingAfter(8);
                document.add(sec2);

                PdfPTable aiTable = new PdfPTable(1);
                aiTable.setWidthPercentage(100);

                PdfPCell aiCell = new PdfPCell();
                aiCell.setBackgroundColor(new Color(238, 242, 255)); // Light indigo tint
                aiCell.setBorderColor(COLOR_PRIMARY);
                aiCell.setPadding(12);

                Paragraph aiSummary = new Paragraph("Potential Annual Savings: " + currency + " " +
                        aiOptimization.getTotalPotentialAnnualSavings().setScale(2).toPlainString() + "/yr", fontAiHeader);
                aiSummary.setSpacingAfter(6);
                aiCell.addElement(aiSummary);

                for (AiRecommendationDto r : aiOptimization.getRecommendations()) {
                    Paragraph item = new Paragraph("• [" + r.getType() + "] " + r.getTitle() + " — " + r.getDescription(), fontAiText);
                    item.setSpacingAfter(4);
                    aiCell.addElement(item);
                }

                aiTable.addCell(aiCell);
                document.add(aiTable);
            }

            // 5. Footer Notice
            Paragraph footer = new Paragraph("Generated automatically by SubPulse SaaS Risk Engine • https://github.com/siddheshvar-2802/SubPulse", fontCardLabel);
            footer.setSpacingBefore(24);
            footer.setAlignment(Element.ALIGN_CENTER);
            document.add(footer);

            document.close();
            return out.toByteArray();
        } catch (Exception e) {
            log.error("Failed to generate PDF document: {}", e.getMessage(), e);
            throw new RuntimeException("PDF generation error: " + e.getMessage(), e);
        }
    }

    private void addKpiCell(PdfPTable table, String label, String value, Font fontLabel, Font fontValue) {
        PdfPCell cell = new PdfPCell();
        cell.setBackgroundColor(COLOR_CARD_BG);
        cell.setBorderColor(COLOR_BORDER);
        cell.setPadding(10);
        cell.addElement(new Paragraph(label, fontLabel));
        Paragraph val = new Paragraph(value, fontValue);
        val.setSpacingBefore(2);
        cell.addElement(val);
        table.addCell(cell);
    }

    private void addTableCell(PdfPTable table, String text, Font font, Color bgColor) {
        PdfPCell cell = new PdfPCell(new Phrase(text, font));
        cell.setBackgroundColor(bgColor);
        cell.setBorderColor(COLOR_BORDER);
        cell.setPadding(6);
        table.addCell(cell);
    }

    // ── Rich HTML Email Body ───────────────────────────────────────────────────

    private String buildHtmlEmailBody(User user, AnalyticsResponse analytics,
                                      AiOptimizationResponse aiOptimization, String currency, String monthYear) {

        return """
                <!DOCTYPE html>
                <html>
                <head>
                    <meta charset="utf-8">
                    <style>
                        body { font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, Helvetica, Arial, sans-serif; background-color: #0b0f19; color: #f8fafc; margin: 0; padding: 24px; }
                        .container { max-width: 600px; margin: 0 auto; background: #111827; border-radius: 16px; border: 1px solid #1f2937; overflow: hidden; }
                        .header { background: linear-gradient(135deg, #6366f1, #a855f7); padding: 32px 24px; text-align: center; }
                        .header h1 { margin: 0; color: #ffffff; font-size: 22px; font-weight: 700; letter-spacing: -0.5px; }
                        .header p { margin: 6px 0 0; color: #e0e7ff; font-size: 13px; }
                        .content { padding: 24px; }
                        .stats-grid { display: table; width: 100%%; margin-bottom: 24px; }
                        .stat-box { display: table-cell; width: 50%%; padding: 14px; background: #1e293b; border-radius: 10px; margin: 6px; }
                        .stat-label { font-size: 11px; text-transform: uppercase; color: #94a3b8; font-weight: 600; }
                        .stat-val { font-size: 20px; font-weight: 700; color: #ffffff; margin-top: 4px; }
                        .ai-box { background: rgba(99, 102, 241, 0.1); border: 1px solid rgba(99, 102, 241, 0.3); border-radius: 12px; padding: 16px; margin-bottom: 24px; }
                        .ai-title { font-weight: 700; color: #a5b4fc; font-size: 14px; margin-bottom: 8px; }
                        .footer { text-align: center; padding: 20px; font-size: 12px; color: #64748b; border-top: 1px solid #1f2937; }
                    </style>
                </head>
                <body>
                    <div class="container">
                        <div class="header">
                            <h1>⚡ SubPulse Executive Digest</h1>
                            <p>%s • Prepared for %s</p>
                        </div>
                        <div class="content">
                            <p style="font-size: 14px; color: #cbd5e1; margin-bottom: 20px;">
                                Hi <strong>%s</strong>,<br>
                                Here is your monthly subscription spending summary and AI cost optimization breakdown. Your full official PDF report is attached to this email.
                            </p>
                            
                            <table style="width: 100%%; border-spacing: 10px; margin-bottom: 20px;">
                                <tr>
                                    <td style="background: #1e293b; padding: 16px; border-radius: 10px;">
                                        <div style="font-size: 11px; color: #94a3b8; font-weight: 600; text-transform: uppercase;">Monthly Spend</div>
                                        <div style="font-size: 20px; font-weight: 700; color: #ffffff; margin-top: 4px;">%s %s</div>
                                    </td>
                                    <td style="background: #1e293b; padding: 16px; border-radius: 10px;">
                                        <div style="font-size: 11px; color: #94a3b8; font-weight: 600; text-transform: uppercase;">Annual Commitment</div>
                                        <div style="font-size: 20px; font-weight: 700; color: #ffffff; margin-top: 4px;">%s %s</div>
                                    </td>
                                </tr>
                                <tr>
                                    <td style="background: #1e293b; padding: 16px; border-radius: 10px;">
                                        <div style="font-size: 11px; color: #94a3b8; font-weight: 600; text-transform: uppercase;">Active Subscriptions</div>
                                        <div style="font-size: 20px; font-weight: 700; color: #ffffff; margin-top: 4px;">%d Services</div>
                                    </td>
                                    <td style="background: #1e293b; padding: 16px; border-radius: 10px;">
                                        <div style="font-size: 11px; color: #94a3b8; font-weight: 600; text-transform: uppercase;">AI Health Score</div>
                                        <div style="font-size: 20px; font-weight: 700; color: #10b981; margin-top: 4px;">%d / 100</div>
                                    </td>
                                </tr>
                            </table>

                            <div class="ai-box">
                                <div class="ai-title">🤖 AI Savings Opportunity: %s %s / year</div>
                                <p style="font-size: 12.5px; color: #cbd5e1; margin: 0; line-height: 1.5;">
                                    SubPulse detected potential arbitrage and duplicate reductions across your subscriptions. Check the attached PDF for itemized savings recommendations.
                                </p>
                            </div>
                            
                            <p style="font-size: 12px; color: #94a3b8; text-align: center;">
                                📎 <em>Your complete monthly report has been generated and attached as a PDF.</em>
                            </p>
                        </div>
                        <div class="footer">
                            SubPulse — Smart SaaS Renewal & Risk Engine<br>
                            &copy; 2026 SubPulse. All rights reserved.
                        </div>
                    </div>
                </body>
                </html>
                """.formatted(
                monthYear,
                user.getFullName(),
                user.getFullName(),
                currency, analytics.getMonthlySpend().setScale(2).toPlainString(),
                currency, analytics.getAnnualSpend().setScale(2).toPlainString(),
                analytics.getTotalActiveSubscriptions(),
                aiOptimization.getHealthScore(),
                currency, aiOptimization.getTotalPotentialAnnualSavings().setScale(2).toPlainString()
        );
    }
}
