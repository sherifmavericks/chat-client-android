package com.openchat.secureim.mms;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Build;
import android.provider.ContactsContract;
import android.provider.MediaStore;
import android.provider.OpenableColumns;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.text.TextUtils;
import android.util.Log;
import android.view.View;
import android.widget.Toast;

import com.google.android.gms.common.GooglePlayServicesNotAvailableException;
import com.google.android.gms.common.GooglePlayServicesRepairableException;
import com.google.android.gms.location.places.ui.PlacePicker;

import com.openchat.secureim.MediaPreviewActivity;
import com.openchat.secureim.R;
import com.openchat.secureim.components.AudioView;
import com.openchat.secureim.components.DocumentView;
import com.openchat.secureim.components.RemovableEditableMediaView;
import com.openchat.secureim.components.ThumbnailView;
import com.openchat.secureim.components.location.openchatMapView;
import com.openchat.secureim.components.location.openchatPlace;
import com.openchat.secureim.crypto.MasterSecret;
import com.openchat.secureim.giph.ui.GiphyActivity;
import com.openchat.secureim.permissions.Permissions;
import com.openchat.secureim.providers.PersistentBlobProvider;
import com.openchat.secureim.scribbles.ScribbleActivity;
import com.openchat.secureim.util.BitmapUtil;
import com.openchat.secureim.util.MediaUtil;
import com.openchat.secureim.util.ViewUtil;
import com.openchat.secureim.util.concurrent.AssertedSuccessListener;
import com.openchat.secureim.util.concurrent.ListenableFuture;
import com.openchat.secureim.util.concurrent.ListenableFuture.Listener;
import com.openchat.secureim.util.views.Stub;
import com.openchat.libim.util.guava.Optional;

import java.io.IOException;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.ExecutionException;


public class AttachmentManager {

  private final static String TAG = AttachmentManager.class.getSimpleName();

  private final @NonNull Context                    context;
  private final @NonNull Stub<View>                 attachmentViewStub;
  private final @NonNull AttachmentListener         attachmentListener;

  private RemovableEditableMediaView removableMediaView;
  private ThumbnailView              thumbnail;
  private AudioView                  audioView;
  private DocumentView               documentView;
  private openchatMapView              mapView;

  private @NonNull  List<Uri>       garbage = new LinkedList<>();
  private @NonNull  Optional<Slide> slide   = Optional.absent();
  private @Nullable Uri             captureUri;

  public AttachmentManager(@NonNull Activity activity, @NonNull AttachmentListener listener) {
    this.context            = activity;
    this.attachmentListener = listener;
    this.attachmentViewStub = ViewUtil.findStubById(activity, R.id.attachment_editor_stub);
  }

  private void inflateStub() {
    if (!attachmentViewStub.resolved()) {
      View root = attachmentViewStub.get();

      this.thumbnail          = ViewUtil.findById(root, R.id.attachment_thumbnail);
      this.audioView          = ViewUtil.findById(root, R.id.attachment_audio);
      this.documentView       = ViewUtil.findById(root, R.id.attachment_document);
      this.mapView            = ViewUtil.findById(root, R.id.attachment_location);
      this.removableMediaView = ViewUtil.findById(root, R.id.removable_media_view);

      removableMediaView.setRemoveClickListener(new RemoveButtonListener());
      removableMediaView.setEditClickListener(new EditButtonListener());
      thumbnail.setOnClickListener(new ThumbnailClickListener());
    }

  }

  public void clear(@NonNull GlideRequests glideRequests, boolean animate) {
    if (attachmentViewStub.resolved()) {

      if (animate) {
        ViewUtil.fadeOut(attachmentViewStub.get(), 200).addListener(new Listener<Boolean>() {
          @Override
          public void onSuccess(Boolean result) {
            thumbnail.clear(glideRequests);
            attachmentViewStub.get().setVisibility(View.GONE);
            attachmentListener.onAttachmentChanged();
          }

          @Override
          public void onFailure(ExecutionException e) {
          }
        });
      } else {
        thumbnail.clear(glideRequests);
        attachmentViewStub.get().setVisibility(View.GONE);
        attachmentListener.onAttachmentChanged();
      }

      markGarbage(getSlideUri());
      slide = Optional.absent();

      audioView.cleanup();
    }
  }

  public void cleanup() {
    cleanup(captureUri);
    cleanup(getSlideUri());

    captureUri = null;
    slide      = Optional.absent();

    Iterator<Uri> iterator = garbage.listIterator();

    while (iterator.hasNext()) {
      cleanup(iterator.next());
      iterator.remove();
    }
  }

  private void cleanup(final @Nullable Uri uri) {
    if (uri != null && PersistentBlobProvider.isAuthority(context, uri)) {
      Log.w(TAG, "cleaning up " + uri);
      PersistentBlobProvider.getInstance(context).delete(uri);
    }
  }

  private void markGarbage(@Nullable Uri uri) {
    if (uri != null && PersistentBlobProvider.isAuthority(context, uri)) {
      Log.w(TAG, "Marking garbage that needs cleaning: " + uri);
      garbage.add(uri);
    }
  }

  private void setSlide(@NonNull Slide slide) {
    if (getSlideUri() != null)                                    cleanup(getSlideUri());
    if (captureUri != null && !captureUri.equals(slide.getUri())) cleanup(captureUri);

    this.captureUri = null;
    this.slide      = Optional.of(slide);
  }

  public void setLocation(@NonNull final MasterSecret masterSecret,
                          @NonNull final openchatPlace place,
                          @NonNull final MediaConstraints constraints)
  {
    inflateStub();

    ListenableFuture<Bitmap> future = mapView.display(place);

    attachmentViewStub.get().setVisibility(View.VISIBLE);
    removableMediaView.display(mapView, false);

    future.addListener(new AssertedSuccessListener<Bitmap>() {
      @Override
      public void onSuccess(@NonNull Bitmap result) {
        byte[]        blob          = BitmapUtil.toByteArray(result);
        Uri           uri           = PersistentBlobProvider.getInstance(context)
                                                            .create(masterSecret, blob, MediaUtil.IMAGE_PNG, null);
        LocationSlide locationSlide = new LocationSlide(context, uri, blob.length, place);

        setSlide(locationSlide);
        attachmentListener.onAttachmentChanged();
      }
    });
  }

  @SuppressLint("StaticFieldLeak")
  public void setMedia(@NonNull final MasterSecret masterSecret,
                       @NonNull final GlideRequests glideRequests,
                       @NonNull final Uri uri,
                       @NonNull final MediaType mediaType,
                       @NonNull final MediaConstraints constraints)
  {
    inflateStub();

            new AsyncTask<Void, Void, Slide>() {
      @Override
      protected void onPreExecute() {
        thumbnail.clear(glideRequests);
        thumbnail.showProgressSpinner();
        attachmentViewStub.get().setVisibility(View.VISIBLE);
      }

      @Override
      protected @Nullable Slide doInBackground(Void... params) {
        try {
          if (PartAuthority.isLocalUri(uri)) {
            return getManuallyCalculatedSlideInfo(uri);
          } else {
            Slide result = getContentResolverSlideInfo(uri);

            if (result == null) return getManuallyCalculatedSlideInfo(uri);
            else                return result;
          }
        } catch (IOException e) {
          Log.w(TAG, e);
          return null;
        }
      }

      @Override
      protected void onPostExecute(@Nullable final Slide slide) {
        if (slide == null) {
          attachmentViewStub.get().setVisibility(View.GONE);
          Toast.makeText(context,
                         R.string.ConversationActivity_sorry_there_was_an_error_setting_your_attachment,
                         Toast.LENGTH_SHORT).show();
        } else if (!areConstraintsSatisfied(context, masterSecret, slide, constraints)) {
          attachmentViewStub.get().setVisibility(View.GONE);
          Toast.makeText(context,
                         R.string.ConversationActivity_attachment_exceeds_size_limits,
                         Toast.LENGTH_SHORT).show();
        } else {
          setSlide(slide);
          attachmentViewStub.get().setVisibility(View.VISIBLE);

          if (slide.hasAudio()) {
            audioView.setAudio(masterSecret, (AudioSlide) slide, false);
            removableMediaView.display(audioView, false);
          } else if (slide.hasDocument()) {
            documentView.setDocument((DocumentSlide) slide, false);
            removableMediaView.display(documentView, false);
          } else {
            thumbnail.setImageResource(masterSecret, glideRequests, slide, false, true);
            removableMediaView.display(thumbnail, mediaType == MediaType.IMAGE);
          }

          attachmentListener.onAttachmentChanged();
        }
      }

      private @Nullable Slide getContentResolverSlideInfo(Uri uri) {
        Cursor cursor = null;
        long   start  = System.currentTimeMillis();

        try {
          cursor = context.getContentResolver().query(uri, null, null, null, null);

          if (cursor != null && cursor.moveToFirst()) {
            String fileName = cursor.getString(cursor.getColumnIndexOrThrow(OpenableColumns.DISPLAY_NAME));
            long   fileSize = cursor.getLong(cursor.getColumnIndexOrThrow(OpenableColumns.SIZE));
            String mimeType = context.getContentResolver().getType(uri);

            Log.w(TAG, "remote slide with size " + fileSize + " took " + (System.currentTimeMillis() - start) + "ms");
            return mediaType.createSlide(context, uri, fileName, mimeType, fileSize);
          }
        } finally {
          if (cursor != null) cursor.close();
        }

        return null;
      }

      private @NonNull Slide getManuallyCalculatedSlideInfo(Uri uri) throws IOException {
        long start      = System.currentTimeMillis();
        Long mediaSize  = null;
        String fileName = null;
        String mimeType = null;

        if (PartAuthority.isLocalUri(uri)) {
          mediaSize = PartAuthority.getAttachmentSize(context, masterSecret, uri);
          fileName  = PartAuthority.getAttachmentFileName(context, masterSecret, uri);
          mimeType  = PartAuthority.getAttachmentContentType(context, masterSecret, uri);
        }

        if (mediaSize == null) {
          mediaSize = MediaUtil.getMediaSize(context, masterSecret, uri);
        }

        Log.w(TAG, "local slide with size " + mediaSize + " took " + (System.currentTimeMillis() - start) + "ms");
        return mediaType.createSlide(context, uri, fileName, mimeType, mediaSize);
      }
    }.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
  }

  public boolean isAttachmentPresent() {
    return attachmentViewStub.resolved() && attachmentViewStub.get().getVisibility() == View.VISIBLE;
  }

  public @NonNull SlideDeck buildSlideDeck() {
    SlideDeck deck = new SlideDeck();
    if (slide.isPresent()) deck.addSlide(slide.get());
    return deck;
  }

  public static void selectDocument(Activity activity, int requestCode) {
    Permissions.with(activity)
               .request(Manifest.permission.WRITE_EXTERNAL_STORAGE)
               .ifNecessary()
               .withPermanentDenialDialog(activity.getString(R.string.AttachmentManager_openchat_requires_the_external_storage_permission_in_order_to_attach_photos_videos_or_audio))
               .onAllGranted(() -> selectMediaType(activity, "*/*", null, requestCode))
               .execute();
  }

  public static void selectGallery(Activity activity, int requestCode) {
    Permissions.with(activity)
               .request(Manifest.permission.WRITE_EXTERNAL_STORAGE)
               .ifNecessary()
               .withPermanentDenialDialog(activity.getString(R.string.AttachmentManager_openchat_requires_the_external_storage_permission_in_order_to_attach_photos_videos_or_audio))
               .onAllGranted(() -> selectMediaType(activity, "image/*", new String[] {"image/*", "video/*"}, requestCode))
               .execute();
  }

  public static void selectAudio(Activity activity, int requestCode) {
    Permissions.with(activity)
               .request(Manifest.permission.WRITE_EXTERNAL_STORAGE)
               .ifNecessary()
               .withPermanentDenialDialog(activity.getString(R.string.AttachmentManager_openchat_requires_the_external_storage_permission_in_order_to_attach_photos_videos_or_audio))
               .onAllGranted(() -> selectMediaType(activity, "audio/*", null, requestCode))
               .execute();
  }

  public static void selectContactInfo(Activity activity, int requestCode) {
    Permissions.with(activity)
               .request(Manifest.permission.WRITE_CONTACTS)
               .ifNecessary()
               .withPermanentDenialDialog(activity.getString(R.string.AttachmentManager_openchat_requires_contacts_permission_in_order_to_attach_contact_information))
               .onAllGranted(() -> {
                 Intent intent = new Intent(Intent.ACTION_PICK, ContactsContract.Contacts.CONTENT_URI);
                 activity.startActivityForResult(intent, requestCode);
               })
               .execute();
  }

  public static void selectLocation(Activity activity, int requestCode) {
    Permissions.with(activity)
               .request(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)
               .ifNecessary()
               .withPermanentDenialDialog(activity.getString(R.string.AttachmentManager_openchat_requires_location_information_in_order_to_attach_a_location))
               .onAllGranted(() -> {
                 try {
                   activity.startActivityForResult(new PlacePicker.IntentBuilder().build(activity), requestCode);
                 } catch (GooglePlayServicesRepairableException | GooglePlayServicesNotAvailableException e) {
                   Log.w(TAG, e);
                 }
               })
               .execute();
  }

  public static void selectGif(Activity activity, int requestCode, boolean isForMms) {
    Intent intent = new Intent(activity, GiphyActivity.class);
    intent.putExtra(GiphyActivity.EXTRA_IS_MMS, isForMms);
    activity.startActivityForResult(intent, requestCode);
  }

  private @Nullable Uri getSlideUri() {
    return slide.isPresent() ? slide.get().getUri() : null;
  }

  public @Nullable Uri getCaptureUri() {
    return captureUri;
  }

  public void capturePhoto(Activity activity, int requestCode) {
    Permissions.with(activity)
               .request(Manifest.permission.CAMERA)
               .ifNecessary()
               .withPermanentDenialDialog(activity.getString(R.string.AttachmentManager_openchat_requires_the_camera_permission_in_order_to_take_photos_but_it_has_been_permanently_denied))
               .onAllGranted(() -> {
                 try {
                   Intent captureIntent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
                   if (captureIntent.resolveActivity(activity.getPackageManager()) != null) {
                     if (captureUri == null) {
                       captureUri = PersistentBlobProvider.getInstance(context)
                                                          .createForExternal(MediaUtil.IMAGE_JPEG);
                     }
                     Log.w(TAG, "captureUri path is " + captureUri.getPath());
                     captureIntent.putExtra(MediaStore.EXTRA_OUTPUT, captureUri);
                     activity.startActivityForResult(captureIntent, requestCode);
                   }
                 } catch (IOException ioe) {
                   Log.w(TAG, ioe);
                 }
               })
               .execute();
  }

  private static void selectMediaType(Activity activity, @NonNull String type, @Nullable String[] extraMimeType, int requestCode) {
    final Intent intent = new Intent();
    intent.setType(type);

    if (extraMimeType != null && Build.VERSION.SDK_INT >= 19) {
      intent.putExtra(Intent.EXTRA_MIME_TYPES, extraMimeType);
    }

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
      intent.setAction(Intent.ACTION_OPEN_DOCUMENT);
      try {
        activity.startActivityForResult(intent, requestCode);
        return;
      } catch (ActivityNotFoundException anfe) {
        Log.w(TAG, "couldn't complete ACTION_OPEN_DOCUMENT, no activity found. falling back.");
      }
    }

    intent.setAction(Intent.ACTION_GET_CONTENT);

    try {
      activity.startActivityForResult(intent, requestCode);
    } catch (ActivityNotFoundException anfe) {
      Log.w(TAG, "couldn't complete ACTION_GET_CONTENT intent, no activity found. falling back.");
      Toast.makeText(activity, R.string.AttachmentManager_cant_open_media_selection, Toast.LENGTH_LONG).show();
    }
  }

  private boolean areConstraintsSatisfied(final @NonNull  Context context,
                                          final @NonNull  MasterSecret masterSecret,
                                          final @Nullable Slide slide,
                                          final @NonNull  MediaConstraints constraints)
  {
   return slide == null                                                        ||
          constraints.isSatisfied(context, masterSecret, slide.asAttachment()) ||
          constraints.canResize(slide.asAttachment());
  }

  private void previewImageDraft(final @NonNull Slide slide) {
    if (MediaPreviewActivity.isContentTypeSupported(slide.getContentType()) && slide.getUri() != null) {
      Intent intent = new Intent(context, MediaPreviewActivity.class);
      intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
      intent.putExtra(MediaPreviewActivity.SIZE_EXTRA, slide.asAttachment().getSize());
      intent.putExtra(MediaPreviewActivity.OUTGOING_EXTRA, true);
      intent.setDataAndType(slide.getUri(), slide.getContentType());

      context.startActivity(intent);
    }
  }

  private class ThumbnailClickListener implements View.OnClickListener {
    @Override
    public void onClick(View v) {
      if (slide.isPresent()) previewImageDraft(slide.get());
    }
  }

  private class RemoveButtonListener implements View.OnClickListener {
    @Override
    public void onClick(View v) {
      cleanup();
      clear(GlideApp.with(context.getApplicationContext()), true);
    }
  }

  private class EditButtonListener implements View.OnClickListener {
    @Override
    public void onClick(View v) {
      Intent intent = new Intent(context, ScribbleActivity.class);
      intent.setData(getSlideUri());
      ((Activity)context).startActivityForResult(intent, ScribbleActivity.SCRIBBLE_REQUEST_CODE);
    }
  }

  public interface AttachmentListener {
    void onAttachmentChanged();
  }

  public enum MediaType {
    IMAGE, GIF, AUDIO, VIDEO, DOCUMENT;

    public @NonNull Slide createSlide(@NonNull  Context context,
                                      @NonNull  Uri     uri,
                                      @Nullable String fileName,
                                      @Nullable String mimeType,
                                                long    dataSize)
    {
      if (mimeType == null) {
        mimeType = "application/octet-stream";
      }

      switch (this) {
      case IMAGE:    return new ImageSlide(context, uri, dataSize);
      case GIF:      return new GifSlide(context, uri, dataSize);
      case AUDIO:    return new AudioSlide(context, uri, dataSize, false);
      case VIDEO:    return new VideoSlide(context, uri, dataSize);
      case DOCUMENT: return new DocumentSlide(context, uri, mimeType, dataSize, fileName);
      default:       throw  new AssertionError("unrecognized enum");
      }
    }

    public static @Nullable MediaType from(final @Nullable String mimeType) {
      if (TextUtils.isEmpty(mimeType))     return null;
      if (MediaUtil.isGif(mimeType))       return GIF;
      if (MediaUtil.isImageType(mimeType)) return IMAGE;
      if (MediaUtil.isAudioType(mimeType)) return AUDIO;
      if (MediaUtil.isVideoType(mimeType)) return VIDEO;

      return DOCUMENT;
    }

  }
}