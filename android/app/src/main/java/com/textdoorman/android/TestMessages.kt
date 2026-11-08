package com.textdoorman.android

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Person
import android.content.Context

/** Posts fake incoming texts so users (and we) can see screening work. */
object TestMessages {
    const val CHANNEL = "screener_test"

    const val SPAM = "URGENT: We're 3X-MATCHING every donation before midnight! " +
        "Chip in \$5 now to keep the Senate >> i.example-pac.com/x9 Stop2End"
    const val NORMAL = "Your package was delivered to your front door. Track: a.co/d/x1y2z3"

    fun post(ctx: Context, body: String) {
        val nm = ctx.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL, "Test messages", NotificationManager.IMPORTANCE_DEFAULT))
        val sender = Person.Builder().setName("(555) 010-%04d".format((0..9999).random())).build()
        val n = Notification.Builder(ctx, CHANNEL)
            .setSmallIcon(R.drawable.ic_stat_shield)
            .setStyle(Notification.MessagingStyle(Person.Builder().setName("Me").build())
                .addMessage(body, System.currentTimeMillis(), sender))
            .setAutoCancel(true)
            .build()
        nm.notify((0..1_000_000).random(), n)
    }
}
