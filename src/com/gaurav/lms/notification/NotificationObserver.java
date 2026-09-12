package com.gaurav.lms.notification;

import com.gaurav.lms.domain.Notification;

/** Observer abstraction; plug in email, SMS, Kafka, etc. in production. */
public interface NotificationObserver {
    void notify(Notification notification);
}
