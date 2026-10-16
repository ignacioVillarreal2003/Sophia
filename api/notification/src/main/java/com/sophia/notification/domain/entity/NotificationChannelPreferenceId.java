package com.sophia.notification.domain.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.UUID;

@Embeddable
@Getter
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode
public class NotificationChannelPreferenceId implements Serializable {

    @Column(name = "user_id")
    private UUID userId;

    @Column(name = "channel", length = 20)
    private String channel;
}
