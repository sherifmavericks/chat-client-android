package com.openchat.secureim.components.reminder;

import android.content.Context;
import android.os.AsyncTask;
import android.support.annotation.NonNull;
import android.view.View;
import android.view.View.OnClickListener;

import com.openchat.secureim.R;
import com.openchat.secureim.database.DatabaseFactory;
import com.openchat.secureim.recipients.Recipient;

public class InviteReminder extends Reminder {

  public InviteReminder(final @NonNull Context context,
                        final @NonNull Recipient recipient)
  {
    super(context.getString(R.string.reminder_header_invite_title),
          context.getString(R.string.reminder_header_invite_text, recipient.toShortString()));

    setDismissListener(new OnClickListener() {
      @Override public void onClick(View v) {
        new AsyncTask<Void,Void,Void>() {

          @Override protected Void doInBackground(Void... params) {
            DatabaseFactory.getRecipientDatabase(context).setSeenInviteReminder(recipient, true);
            return null;
          }
        }.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
      }
    });
  }
}
