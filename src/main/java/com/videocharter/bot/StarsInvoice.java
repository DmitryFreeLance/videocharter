package com.videocharter.bot;

import java.util.List;
import org.apache.commons.lang3.StringUtils;
import org.telegram.telegrambots.meta.api.methods.invoices.SendInvoice;
import org.telegram.telegrambots.meta.api.objects.payments.LabeledPrice;
import org.telegram.telegrambots.meta.exceptions.TelegramApiValidationException;

/**
 * Telegram Stars invoices must omit provider_token, but the current Java SDK
 * still validates it as required. This wrapper keeps validation aligned with
 * the Bot API for XTR payments.
 */
public class StarsInvoice extends SendInvoice {

    @Override
    public void validate() throws TelegramApiValidationException {
        if (StringUtils.isEmpty(getChatId())) {
            throw new TelegramApiValidationException("ChatId parameter can't be empty", this);
        }
        if (StringUtils.isEmpty(getTitle()) || getTitle().length() > 32) {
            throw new TelegramApiValidationException("Title parameter can't be empty or longer than 32 chars", this);
        }
        if (StringUtils.isEmpty(getDescription()) || getDescription().length() > 255) {
            throw new TelegramApiValidationException("Description parameter can't be empty or longer than 255 chars", this);
        }
        if (StringUtils.isEmpty(getPayload())) {
            throw new TelegramApiValidationException("Payload parameter can't be empty", this);
        }
        if (StringUtils.isEmpty(getCurrency())) {
            throw new TelegramApiValidationException("Currency parameter can't be empty", this);
        }
        List<LabeledPrice> prices = getPrices();
        if (prices == null || prices.isEmpty()) {
            throw new TelegramApiValidationException("Prices parameter can't be empty", this);
        }
        for (LabeledPrice price : prices) {
            price.validate();
        }
        if (getReplyMarkup() != null) {
            getReplyMarkup().validate();
        }
        if (getReplyParameters() != null) {
            getReplyParameters().validate();
        }
    }
}
