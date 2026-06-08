package com.backend.service;

import com.backend.dto.cart.CartItemResponseDTO;
import com.backend.dto.cart.CartResponseDTO;
import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

@Service
@RequiredArgsConstructor
@Slf4j
public class CheckoutEmailService {

    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    private final JavaMailSender mailSender;

    @Value("${spring.mail.username:}")
    private String senderAddress;

    @Value("${app.checkout.courses-url:http://localhost:4200/courses}")
    private String coursesUrl;

    public void sendCheckoutReceipt(String recipientEmail, CartResponseDTO cart) {
        if (recipientEmail == null || recipientEmail.isBlank()) {
            log.warn("Skipping checkout email: recipient email is empty.");
            return;
        }
        if (cart == null || cart.getItems() == null || cart.getItems().isEmpty()) {
            log.warn("Skipping checkout email: checkout cart is empty for recipient {}", recipientEmail);
            return;
        }

        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, false, StandardCharsets.UTF_8.name());
            helper.setTo(recipientEmail);
            helper.setSubject("YBrainy Invoice #" + cart.getId());
            if (senderAddress != null && !senderAddress.isBlank()) {
                helper.setFrom(senderAddress);
            }
            helper.setText(buildInvoiceHtml(recipientEmail, cart), true);
            mailSender.send(message);
            log.info("Checkout receipt sent successfully to {}", recipientEmail);
        } catch (Exception ex) {
            log.warn("Checkout succeeded but failed to send receipt email to {}: {}", recipientEmail, ex.getMessage());
        }
    }

    private String buildInvoiceHtml(String recipientEmail, CartResponseDTO cart) {
        StringBuilder itemRows = new StringBuilder();
        int index = 1;
        for (CartItemResponseDTO item : cart.getItems()) {
            int quantity = item.getQuantity() == null ? 0 : item.getQuantity();
            double unitPrice = item.getPriceAtPurchase() == null ? 0.0 : item.getPriceAtPurchase();
            double lineTotal = item.getSubtotal() == null ? unitPrice * quantity : item.getSubtotal();
            itemRows.append("<tr>")
                    .append("<td style=\"padding:12px;border-bottom:1px solid #e5e7eb;\">").append(index++).append("</td>")
                    .append("<td style=\"padding:12px;border-bottom:1px solid #e5e7eb;font-weight:600;\">")
                    .append(escapeHtml(item.getPackTitle() == null ? "Learning Pack" : item.getPackTitle()))
                    .append("</td>")
                    .append("<td style=\"padding:12px;border-bottom:1px solid #e5e7eb;\">E-Learning Pack</td>")
                    .append("<td style=\"padding:12px;border-bottom:1px solid #e5e7eb;text-align:right;\">$")
                    .append(formatAmount(unitPrice))
                    .append("</td>")
                    .append("<td style=\"padding:12px;border-bottom:1px solid #e5e7eb;text-align:center;\">")
                    .append(quantity)
                    .append("</td>")
                    .append("<td style=\"padding:12px;border-bottom:1px solid #e5e7eb;text-align:right;font-weight:600;\">$")
                    .append(formatAmount(lineTotal))
                    .append("</td>")
                    .append("</tr>");
        }

        double subtotal = computeSubtotal(cart);
        double total = cart.getTotalAmount() == null ? subtotal : cart.getTotalAmount();
        String qrImageUrl = "https://api.qrserver.com/v1/create-qr-code/?size=180x180&data="
                + URLEncoder.encode(coursesUrl, StandardCharsets.UTF_8);

        return new StringBuilder()
                .append("<html>")
                .append("<body style=\"font-family:Arial,sans-serif;background:#f8fafc;padding:24px;color:#111827;\">")
                .append("<div style=\"max-width:760px;margin:0 auto;background:#ffffff;border-radius:20px;padding:32px;box-shadow:0 24px 50px rgba(15,23,42,0.08);\">")
                .append("<div style=\"display:flex;justify-content:space-between;align-items:flex-start;gap:24px;\">")
                .append("<div>")
                .append("<p style=\"margin:0 0 8px;color:#0f766e;font-weight:700;letter-spacing:0.08em;text-transform:uppercase;\">YBrainy Checkout</p>")
                .append("<h1 style=\"margin:0 0 8px;font-size:28px;\">Invoice #").append(cart.getId()).append("</h1>")
                .append("<p style=\"margin:0;color:#475569;\">Issued on ")
                .append(LocalDate.now().format(DATE_FORMATTER)).append("</p>")
                .append("<p style=\"margin:8px 0 0;color:#475569;\">Recipient: ")
                .append(escapeHtml(recipientEmail)).append("</p>")
                .append("</div>")
                .append("<img src=\"").append(qrImageUrl)
                .append("\" alt=\"Courses QR\" style=\"width:120px;height:120px;border-radius:16px;border:1px solid #e2e8f0;\"/>")
                .append("</div>")
                .append("<table style=\"width:100%;border-collapse:collapse;margin-top:28px;\">")
                .append("<thead><tr style=\"text-align:left;background:#f8fafc;\">")
                .append("<th style=\"padding:12px;\">#</th>")
                .append("<th style=\"padding:12px;\">Pack</th>")
                .append("<th style=\"padding:12px;\">Type</th>")
                .append("<th style=\"padding:12px;text-align:right;\">Unit Price</th>")
                .append("<th style=\"padding:12px;text-align:center;\">Qty</th>")
                .append("<th style=\"padding:12px;text-align:right;\">Total</th>")
                .append("</tr></thead>")
                .append("<tbody>").append(itemRows).append("</tbody>")
                .append("</table>")
                .append("<div style=\"margin-top:24px;padding-top:16px;border-top:1px solid #e2e8f0;\">")
                .append("<div style=\"display:flex;justify-content:space-between;margin-bottom:8px;color:#475569;\">")
                .append("<span>Subtotal</span><strong>$").append(formatAmount(subtotal)).append("</strong>")
                .append("</div>")
                .append("<div style=\"display:flex;justify-content:space-between;font-size:20px;\">")
                .append("<span>Total</span><strong>$").append(formatAmount(total)).append("</strong>")
                .append("</div>")
                .append("</div>")
                .append("<p style=\"margin-top:24px;color:#475569;\">")
                .append("Your packs are ready at ")
                .append("<a href=\"").append(escapeHtml(coursesUrl)).append("\">")
                .append(escapeHtml(coursesUrl)).append("</a>.")
                .append("</p>")
                .append("</div>")
                .append("</body>")
                .append("</html>")
                .toString();
    }

    private double computeSubtotal(CartResponseDTO cart) {
        return cart.getItems().stream()
                .mapToDouble(item -> {
                    if (item.getSubtotal() != null) {
                        return item.getSubtotal();
                    }
                    double unit = item.getPriceAtPurchase() == null ? 0.0 : item.getPriceAtPurchase();
                    int qty = item.getQuantity() == null ? 0 : item.getQuantity();
                    return unit * qty;
                })
                .sum();
    }

    private String escapeHtml(String input) {
        if (input == null) {
            return "";
        }
        return input
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&#39;");
    }

    private String formatAmount(Double amount) {
        double value = amount == null ? 0.0 : amount;
        return String.format(Locale.US, "%.2f", value);
    }
}
