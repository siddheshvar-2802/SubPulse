package com.subpulse.service;

import com.subpulse.dto.request.CsvImportConfirmRequest;
import com.subpulse.dto.request.CsvImportItemRequest;
import com.subpulse.dto.response.CsvImportPreviewDto;
import com.subpulse.dto.response.SubscriptionResponse;
import com.subpulse.entity.Subscription;
import com.subpulse.entity.User;
import com.subpulse.enums.BillingCycle;
import com.subpulse.enums.SubscriptionCategory;
import com.subpulse.exception.ResourceNotFoundException;
import com.subpulse.repository.SubscriptionRepository;
import com.subpulse.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.*;
import java.util.stream.Collectors;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Intelligent bank statement and CSV transaction parser.
 * Automatically recognizes recurring SaaS, AI, and streaming subscriptions.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class BankStatementParserService {

    private final SubscriptionRepository subscriptionRepository;
    private final UserRepository         userRepository;
    private final SubscriptionService    subscriptionService;

    private static final List<ServiceRule> SERVICE_CATALOG = List.of(
            // AI Tools
            new ServiceRule(List.of("CHATGPT", "OPENAI"), "ChatGPT Plus", SubscriptionCategory.AI_TOOLS, "https://chatgpt.com"),
            new ServiceRule(List.of("ANTHROPIC", "CLAUDE.AI", "CLAUDE PRO"), "Claude Pro", SubscriptionCategory.AI_TOOLS, "https://claude.ai"),
            new ServiceRule(List.of("MIDJOURNEY"), "Midjourney", SubscriptionCategory.AI_TOOLS, "https://midjourney.com"),
            new ServiceRule(List.of("CURSOR.SH", "CURSOR AI", "CURSOR"), "Cursor AI", SubscriptionCategory.AI_TOOLS, "https://cursor.com"),
            new ServiceRule(List.of("PERPLEXITY"), "Perplexity Pro", SubscriptionCategory.AI_TOOLS, "https://perplexity.ai"),
            new ServiceRule(List.of("GITHUB COPILOT"), "GitHub Copilot", SubscriptionCategory.AI_TOOLS, "https://github.com/features/copilot"),

            // Entertainment & Streaming
            new ServiceRule(List.of("NETFLIX"), "Netflix", SubscriptionCategory.ENTERTAINMENT, "https://netflix.com"),
            new ServiceRule(List.of("SPOTIFY"), "Spotify", SubscriptionCategory.ENTERTAINMENT, "https://spotify.com"),
            new ServiceRule(List.of("AMAZON PRIME", "AMZN PRIME", "PRIME VIDEO"), "Amazon Prime", SubscriptionCategory.ENTERTAINMENT, "https://amazon.com"),
            new ServiceRule(List.of("APPLE.COM/BILL", "ITUNES.COM", "APPLE MUSIC", "APPLE TV"), "Apple Services", SubscriptionCategory.ENTERTAINMENT, "https://apple.com"),
            new ServiceRule(List.of("YOUTUBE PREMIUM", "GOOGLE YOUTUBE"), "YouTube Premium", SubscriptionCategory.ENTERTAINMENT, "https://youtube.com/premium"),
            new ServiceRule(List.of("DISNEY+", "DISNEY PLUS", "HOTSTAR"), "Disney+ Hotstar", SubscriptionCategory.ENTERTAINMENT, "https://disneyplus.com"),
            new ServiceRule(List.of("HULU"), "Hulu", SubscriptionCategory.ENTERTAINMENT, "https://hulu.com"),
            new ServiceRule(List.of("HBO MAX", "MAX.COM"), "Max (HBO)", SubscriptionCategory.ENTERTAINMENT, "https://max.com"),

            // Developer & Cloud Tools
            new ServiceRule(List.of("GITHUB"), "GitHub", SubscriptionCategory.DEVELOPER_TOOLS, "https://github.com"),
            new ServiceRule(List.of("AWS", "AMAZON WEB SERVICES"), "AWS Cloud", SubscriptionCategory.CLOUD_STORAGE, "https://aws.amazon.com"),
            new ServiceRule(List.of("GOOGLE CLOUD", "GCP"), "Google Cloud", SubscriptionCategory.CLOUD_STORAGE, "https://cloud.google.com"),
            new ServiceRule(List.of("DIGITALOCEAN"), "DigitalOcean", SubscriptionCategory.CLOUD_STORAGE, "https://digitalocean.com"),
            new ServiceRule(List.of("VERCEL"), "Vercel", SubscriptionCategory.DEVELOPER_TOOLS, "https://vercel.com"),
            new ServiceRule(List.of("SUPABASE"), "Supabase", SubscriptionCategory.DEVELOPER_TOOLS, "https://supabase.com"),
            new ServiceRule(List.of("HEROKU"), "Heroku", SubscriptionCategory.CLOUD_STORAGE, "https://heroku.com"),
            new ServiceRule(List.of("JETBRAINS"), "JetBrains All Products", SubscriptionCategory.DEVELOPER_TOOLS, "https://jetbrains.com"),
            new ServiceRule(List.of("POSTMAN"), "Postman", SubscriptionCategory.DEVELOPER_TOOLS, "https://postman.com"),
            new ServiceRule(List.of("DOCKER"), "Docker Desktop", SubscriptionCategory.DEVELOPER_TOOLS, "https://docker.com"),

            // Design & Productivity
            new ServiceRule(List.of("ADOBE"), "Adobe Creative Cloud", SubscriptionCategory.DESIGN, "https://adobe.com"),
            new ServiceRule(List.of("FIGMA"), "Figma", SubscriptionCategory.DESIGN, "https://figma.com"),
            new ServiceRule(List.of("CANVA"), "Canva Pro", SubscriptionCategory.DESIGN, "https://canva.com"),
            new ServiceRule(List.of("NOTION"), "Notion Plus", SubscriptionCategory.PRODUCTIVITY, "https://notion.so"),
            new ServiceRule(List.of("MICROSOFT 365", "MSFT 365", "OFFICE 365"), "Microsoft 365", SubscriptionCategory.PRODUCTIVITY, "https://microsoft.com"),
            new ServiceRule(List.of("GOOGLE STORAGE", "GOOGLE ONE", "GOOGLE WORKSPACE", "GSUITE"), "Google Workspace / One", SubscriptionCategory.PRODUCTIVITY, "https://one.google.com"),
            new ServiceRule(List.of("ZOOM.US", "ZOOM VIDEO"), "Zoom Pro", SubscriptionCategory.COMMUNICATION, "https://zoom.us"),
            new ServiceRule(List.of("SLACK"), "Slack Pro", SubscriptionCategory.COMMUNICATION, "https://slack.com"),
            new ServiceRule(List.of("DISCORD NITRO", "DISCORD"), "Discord Nitro", SubscriptionCategory.COMMUNICATION, "https://discord.com"),
            new ServiceRule(List.of("GRAMMARLY"), "Grammarly Premium", SubscriptionCategory.PRODUCTIVITY, "https://grammarly.com"),

            // Security & Utilities
            new ServiceRule(List.of("NORDVPN"), "NordVPN", SubscriptionCategory.SECURITY, "https://nordvpn.com"),
            new ServiceRule(List.of("1PASSWORD"), "1Password", SubscriptionCategory.SECURITY, "https://1password.com"),
            new ServiceRule(List.of("BITWARDEN"), "Bitwarden Premium", SubscriptionCategory.SECURITY, "https://bitwarden.com"),
            new ServiceRule(List.of("PROTON", "PROTONMAIL"), "Proton Unlimited", SubscriptionCategory.SECURITY, "https://proton.me"),
            new ServiceRule(List.of("DROPBOX"), "Dropbox Plus", SubscriptionCategory.CLOUD_STORAGE, "https://dropbox.com")
    );

    private static final List<DateTimeFormatter> DATE_FORMATTERS = List.of(
            DateTimeFormatter.ofPattern("yyyy-MM-dd"),
            DateTimeFormatter.ofPattern("dd-MM-yyyy"),
            DateTimeFormatter.ofPattern("dd/MM/yyyy"),
            DateTimeFormatter.ofPattern("MM/dd/yyyy"),
            DateTimeFormatter.ofPattern("yyyy/MM/dd"),
            DateTimeFormatter.ofPattern("d/M/yyyy"),
            DateTimeFormatter.ofPattern("d-M-yyyy"),
            DateTimeFormatter.ofPattern("dd-MMM-yyyy", Locale.ENGLISH)
    );

    @Transactional(readOnly = true)
    public List<CsvImportPreviewDto> parseAndPreviewCsv(Long userId, MultipartFile file) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User", userId));

        String defaultCurrency = user.getPreferredCurrency() != null ? user.getPreferredCurrency() : "USD";
        Set<String> existingServiceNames = subscriptionRepository.findByUserId(userId).stream()
                .filter(Subscription::getIsActive)
                .map(s -> s.getServiceName().toLowerCase().trim())
                .collect(Collectors.toSet());

        List<RawTransaction> rawTransactions = parseRawCsv(file, defaultCurrency);
        Map<String, CsvImportPreviewDto> detectedMap = new LinkedHashMap<>();

        int idCounter = 1;
        for (RawTransaction tx : rawTransactions) {
            MatchedService match = matchService(tx.description);
            if (match != null) {
                String cleanName = match.rule.serviceName;
                BigDecimal positiveAmount = tx.amount.abs().setScale(2, RoundingMode.HALF_UP);

                if (positiveAmount.compareTo(BigDecimal.ZERO) <= 0) continue;

                LocalDate txDate = tx.date != null ? tx.date : LocalDate.now();
                LocalDate estimatedNextBilling = txDate.plusMonths(1);

                // If date was in the past, roll forward to future renewal date
                while (estimatedNextBilling.isBefore(LocalDate.now())) {
                    estimatedNextBilling = estimatedNextBilling.plusMonths(1);
                }

                boolean alreadyTracked = existingServiceNames.contains(cleanName.toLowerCase());

                CsvImportPreviewDto dto = CsvImportPreviewDto.builder()
                        .tempId("import-item-" + (idCounter++))
                        .serviceName(cleanName)
                        .rawDescription(tx.description)
                        .amount(positiveAmount)
                        .currency(tx.currency != null ? tx.currency : defaultCurrency)
                        .category(match.rule.category)
                        .billingCycle(BillingCycle.MONTHLY)
                        .transactionDate(txDate)
                        .nextBillingDate(estimatedNextBilling)
                        .websiteUrl(match.rule.websiteUrl)
                        .confidence(match.confidence)
                        .isAlreadyTracked(alreadyTracked)
                        .build();

                // Keep newest transaction if duplicate service found in statement
                detectedMap.put(cleanName.toLowerCase(), dto);
            }
        }

        return new ArrayList<>(detectedMap.values());
    }

    @Transactional
    public List<SubscriptionResponse> confirmImport(Long userId, CsvImportConfirmRequest request) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User", userId));

        List<SubscriptionResponse> importedList = new ArrayList<>();

        for (CsvImportItemRequest item : request.getSubscriptions()) {
            Subscription sub = Subscription.builder()
                    .user(user)
                    .serviceName(item.getServiceName().trim())
                    .description(item.getDescription() != null ? item.getDescription() : "Imported from Bank Statement")
                    .amount(item.getAmount())
                    .currency(item.getCurrency() != null ? item.getCurrency().toUpperCase() : user.getPreferredCurrency())
                    .billingCycle(item.getBillingCycle())
                    .startDate(item.getStartDate() != null ? item.getStartDate() : LocalDate.now())
                    .nextBillingDate(item.getNextBillingDate())
                    .websiteUrl(item.getWebsiteUrl())
                    .category(item.getCategory() != null ? item.getCategory() : SubscriptionCategory.OTHER)
                    .isActive(true)
                    .autoRenew(true)
                    .build();

            Subscription saved = subscriptionRepository.save(sub);
            importedList.add(subscriptionService.getById(userId, saved.getId()));
        }

        log.info("Bulk imported {} subscriptions from statement for user {}", importedList.size(), user.getEmail());
        return importedList;
    }

    public String generateSampleTemplateCsv() {
        return """
                Date,Description,Amount,Currency
                2026-08-01,NETFLIX.COM PAYMENT 9821,19.99,USD
                2026-08-05,OPENAI *CHATGPT SUBSCRIPTION,20.00,USD
                2026-08-08,SPOTIFY USA 10293,10.99,USD
                2026-08-12,AWS EMEA AMZN.COM/BILL,45.20,USD
                2026-08-14,GITHUB PRO SUBSCRIPTION,10.00,USD
                2026-08-15,ADOBE CREATIVE CLOUD,54.99,USD
                2026-08-16,NOTION PLUS MONTHLY,10.00,USD
                """;
    }

    // ── Internal Helpers ───────────────────────────────────────────────────────

    private List<RawTransaction> parseRawCsv(MultipartFile file, String defaultCurrency) {
        List<RawTransaction> transactions = new ArrayList<>();
        if (file == null || file.isEmpty()) return transactions;

        try (BufferedReader reader = new BufferedReader(new InputStreamReader(file.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            int lineNum = 0;
            int dateCol = 0, descCol = 1, amountCol = 2, currCol = -1;
            boolean headerFound = false;

            while ((line = reader.readLine()) != null) {
                lineNum++;
                String trimmed = line.trim();
                if (trimmed.isEmpty()) continue;

                String[] cols = parseCsvLine(trimmed);
                if (cols.length < 2) continue;

                // Look for headers
                if (!headerFound && isHeaderLine(cols)) {
                    for (int i = 0; i < cols.length; i++) {
                        String h = cols[i].toLowerCase();
                        if (h.contains("date") || h.contains("txn") || h.contains("time")) dateCol = i;
                        else if (h.contains("desc") || h.contains("particular") || h.contains("merchant") || h.contains("narration") || h.contains("payee") || h.contains("details")) descCol = i;
                        else if (h.contains("amount") || h.contains("debit") || h.contains("withdrawal") || h.contains("cost")) amountCol = i;
                        else if (h.contains("curr") || h.contains("ccy")) currCol = i;
                    }
                    headerFound = true;
                    continue;
                }

                // Process transaction line
                String dateStr = (dateCol < cols.length) ? cols[dateCol].trim() : "";
                String descStr = (descCol < cols.length) ? cols[descCol].trim() : "";
                String amountStr = (amountCol < cols.length) ? cols[amountCol].trim() : "";
                String currStr = (currCol >= 0 && currCol < cols.length) ? cols[currCol].trim() : defaultCurrency;

                LocalDate date = parseDate(dateStr);
                BigDecimal amount = parseAmount(amountStr);

                if (descStr.length() > 2 && amount.compareTo(BigDecimal.ZERO) != 0) {
                    transactions.add(new RawTransaction(date, descStr, amount, currStr));
                }
            }
        } catch (Exception e) {
            log.error("Error reading CSV file: {}", e.getMessage(), e);
        }

        return transactions;
    }

    private boolean isHeaderLine(String[] cols) {
        String combined = String.join(" ", cols).toLowerCase();
        return combined.contains("date") || combined.contains("desc") || combined.contains("amount") || combined.contains("narration");
    }

    private LocalDate parseDate(String raw) {
        if (raw == null || raw.isBlank()) return LocalDate.now();
        String cleaned = raw.replaceAll("[\"']", "").trim();

        for (DateTimeFormatter fmt : DATE_FORMATTERS) {
            try {
                return LocalDate.parse(cleaned, fmt);
            } catch (DateTimeParseException ignored) {}
        }
        return LocalDate.now();
    }

    private BigDecimal parseAmount(String raw) {
        if (raw == null || raw.isBlank()) return BigDecimal.ZERO;
        try {
            String cleaned = raw.replaceAll("[^0-9.-]", "").trim();
            if (cleaned.isEmpty() || cleaned.equals("-") || cleaned.equals(".")) return BigDecimal.ZERO;
            return new BigDecimal(cleaned);
        } catch (Exception e) {
            return BigDecimal.ZERO;
        }
    }

    private String[] parseCsvLine(String line) {
        List<String> tokens = new ArrayList<>();
        StringBuilder sb = new StringBuilder();
        boolean inQuotes = false;

        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            if (c == '"') {
                inQuotes = !inQuotes;
            } else if ((c == ',' || c == '\t' || c == ';') && !inQuotes) {
                tokens.add(sb.toString().trim().replaceAll("^\"|\"$", ""));
                sb.setLength(0);
            } else {
                sb.append(c);
            }
        }
        tokens.add(sb.toString().trim().replaceAll("^\"|\"$", ""));
        return tokens.toArray(new String[0]);
    }

    private MatchedService matchService(String description) {
        if (description == null) return null;
        String upper = description.toUpperCase();

        for (ServiceRule rule : SERVICE_CATALOG) {
            for (String kw : rule.keywords) {
                if (upper.contains(kw)) {
                    String confidence = upper.startsWith(kw) || upper.contains(" " + kw + " ") ? "HIGH" : "MEDIUM";
                    return new MatchedService(rule, confidence);
                }
            }
        }
        return null;
    }

    // ── Internal Records ───────────────────────────────────────────────────────

    private record ServiceRule(List<String> keywords, String serviceName, SubscriptionCategory category, String websiteUrl) {}
    private record MatchedService(ServiceRule rule, String confidence) {}
    private record RawTransaction(LocalDate date, String description, BigDecimal amount, String currency) {}
}
