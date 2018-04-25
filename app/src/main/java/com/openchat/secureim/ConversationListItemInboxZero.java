package com.openchat.secureim;


import android.content.Context;
import android.os.Build;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.annotation.RequiresApi;
import android.util.AttributeSet;
import android.widget.LinearLayout;

import com.openchat.secureim.crypto.MasterSecret;
import com.openchat.secureim.database.model.ThreadRecord;
import com.openchat.secureim.mms.GlideRequests;

import java.util.Locale;
import java.util.Set;

public class ConversationListItemInboxZero extends LinearLayout implements BindableConversationListItem{
  public ConversationListItemInboxZero(Context context) {
    super(context);
  }

  public ConversationListItemInboxZero(Context context, @Nullable AttributeSet attrs) {
    super(context, attrs);
  }

  public ConversationListItemInboxZero(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
    super(context, attrs, defStyleAttr);
  }

  @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
  public ConversationListItemInboxZero(Context context, AttributeSet attrs, int defStyleAttr, int defStyleRes) {
    super(context, attrs, defStyleAttr, defStyleRes);
  }

  @Override
  public void unbind() {

  }

  @Override
  public void bind(@NonNull MasterSecret masterSecret, @NonNull ThreadRecord thread, @NonNull GlideRequests glideRequests, @NonNull Locale locale, @NonNull Set<Long> selectedThreads, boolean batchMode) {

  }
}
