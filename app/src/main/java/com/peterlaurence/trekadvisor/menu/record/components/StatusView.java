package com.peterlaurence.trekadvisor.menu.record.components;

import android.content.Context;
import android.support.v7.widget.CardView;
import android.util.AttributeSet;

import com.peterlaurence.trekadvisor.R;
import com.peterlaurence.trekadvisor.menu.events.RecordGpxStartEvent;
import com.peterlaurence.trekadvisor.menu.record.components.widgets.HeartBeatIndicator;

import org.greenrobot.eventbus.EventBus;

/**
 * A View-Model that reports the status (started/stopped) of the
 * {@link com.peterlaurence.trekadvisor.service.LocationService}.
 *
 * @author peterLaurence on 23/12/17.
 */
public class StatusView extends CardView {
    private HeartBeatIndicator mHeartBeatIndicator;

    public StatusView(Context context) {
        this(context, null);
    }

    public StatusView(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public StatusView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init(context, attrs);
    }

    private void init(Context context, AttributeSet attrs) {
        inflate(context, R.layout.record_status_layout, this);

        mHeartBeatIndicator = findViewById(R.id.heartBeatIndicator);

        initState();
    }

    /**
     * If there is a subscriber for the {@link RecordGpxStartEvent}, we assume that
     * the {@link com.peterlaurence.trekadvisor.service.LocationService} is started. <br>
     * Else we assume that the service isn't started.
     */
    private void initState() {
        if (EventBus.getDefault().hasSubscriberForEvent(RecordGpxStartEvent.class)) {
            onServiceStarted();
        } else {
            onServiceStopped();
        }
    }

    public void onServiceStarted() {
        mHeartBeatIndicator.beat();
    }

    public void onServiceStopped() {
        mHeartBeatIndicator.off();
    }
}
