package com.jrweb.banksmspopup

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings
import android.provider.Telephony
import java.text.DecimalFormat
import java.util.regex.Pattern

class SmsReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Telephony.Sms.Intents.SMS_RECEIVED_ACTION) {
            val messages = Telephony.Sms.Intents.getMessagesFromIntent(intent)
            val fullBody = StringBuilder()
            var sender = ""

            for (sms in messages) {
                sender = sms.originatingAddress ?: ""
                fullBody.append(sms.messageBody)
            }

            parseBankSms(context, sender, fullBody.toString())
        }
    }

    private fun parseBankSms(context: Context, sender: String, body: String) {
        val bankName = detectBankName(sender, body) ?: return

        val isDeposit = when {
            body.contains("واريز") || body.contains("واریز") || body.contains("انتقال به") || body.contains("+") -> true
            body.contains("برداشت") || body.contains("خريد") || body.contains("خرید") || body.contains("انتقال از") || body.contains("-") -> false
            else -> return
        }

        val amountRaw = extractAmount(body) ?: return
        val amountToman = if (body.contains("تومان")) amountRaw else amountRaw / 10
        val formattedAmount = DecimalFormat("#,###").format(amountToman)

        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M || Settings.canDrawOverlays(context)) {
            val serviceIntent = Intent(context, PopupService::class.java).apply {
                putExtra("BANK_NAME", bankName)
                putExtra("AMOUNT", formattedAmount)
                putExtra("IS_DEPOSIT", isDeposit)
            }
            context.startService(serviceIntent)
        }
    }

    private fun detectBankName(sender: String, body: String): String? {
        val text = "$sender $body"
        val banks = mapOf(
            "ملت" to "بانک ملت",
            "ملي" to "بانک ملی",
            "ملی" to "بانک ملی",
            "صادرات" to "بانک صادرات",
            "تجارت" to "بانک تجارت",
            "سپه" to "بانک سپه",
            "پاسارگاد" to "بانک پاسارگاد",
            "سامان" to "بانک سامان",
            "پارسيان" to "بانک پارسیان",
            "پارسیان" to "بانک پارسیان",
            "کشاورزي" to "بانک کشاورزی",
            "کشاورزی" to "بانک کشاورزی",
            "مسکن" to "بانک مسکن",
            "رفاه" to "بانک رفاه",
            "شهر" to "بانک شهر",
            "بلو" to "بلوبانک",
            "آينده" to "بانک آینده",
            "آینده" to "بانک آینده",
            "رسالت" to "بانک رسالت",
            "مهر" to "بانک مهر ایران"
        )
        for ((key, value) in banks) {
            if (text.contains(key)) return value
        }
        return null
    }

    private fun extractAmount(body: String): Long? {
        val englishBody = body
            .replace("۰", "0").replace("۱", "1").replace("۲", "2")
            .replace("۳", "3").replace("۴", "4").replace("۵", "5")
            .replace("۶", "6").replace("۷", "7").replace("۸", "8")
            .replace("۹", "9")

        val matcher = Pattern.compile("(\\d{1,3}(,\\d{3})+|\\d{4,})").matcher(englishBody)
        while (matcher.find()) {
            val numStr = matcher.group(1)?.replace(",", "") ?: continue
            val value = numStr.toLongOrNull() ?: continue
            if (numStr.length in 4..12) {
                return value
            }
        }
        return null
    }
}