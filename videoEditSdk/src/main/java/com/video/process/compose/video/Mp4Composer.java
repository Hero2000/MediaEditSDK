package com.video.process.compose.video;

import android.media.MediaMetadataRetriever;

import androidx.annotation.NonNull;

import com.video.process.model.ProcessParams;
import com.video.process.model.VideoSize;
import com.video.process.utils.LogUtils;
import com.video.process.utils.WorkThreadHandler;
import com.video.process.preview.filter.GlFilter;
import com.video.process.model.FillMode;
import com.video.process.model.Rotation;
import com.video.process.utils.VideoCustomException;
import com.video.process.compose.filter.IResolutionFilter;

import java.io.File;
import java.io.FileDescriptor;
import java.io.FileInputStream;

public class Mp4Composer {

    private final static String TAG = Mp4Composer.class.getSimpleName();

    private ProcessParams mProcessParams;
    private VideoComposeListener mComposeListener;

    public Mp4Composer(@NonNull ProcessParams params) {
        mProcessParams = params;
    }

    public Mp4Composer listener(@NonNull VideoComposeListener listener) {
        this.mComposeListener = listener;
        return this;
    }

    public Mp4Composer start() {
        WorkThreadHandler.submitRunnableTask(new Runnable() {
            @Override
            public void run() {
                File outputFile = new File(mProcessParams.mDestPath);
                if (outputFile.exists()) {
                    outputFile.delete();
                }
                final File srcFile = new File(mProcessParams.mSrcPath);
                final FileInputStream fileInputStream;
                try {
                    fileInputStream = new FileInputStream(srcFile);
                } catch (Exception e) {
                    VideoCustomException exception = new VideoCustomException(VideoCustomException.SRC_VIDEO_FILE_ERROR, e);
                    if (mComposeListener != null) {
                        mComposeListener.onFailed(exception);
                    }
                    return;
                }
                if (fileInputStream == null) {
                    VideoCustomException exception = new VideoCustomException(VideoCustomException.SRC_VIDEO_FILE_ERROR2, new Throwable());
                    if (mComposeListener != null) {
                        mComposeListener.onFailed(exception);
                    }
                    return;
                }
                FileDescriptor fd;
                try {
                    fd = fileInputStream.getFD();
                } catch (Exception e) {
                    VideoCustomException exception = new VideoCustomException(VideoCustomException.SRC_VIDEO_FILE_ERROR3, e);
                    if (mComposeListener != null) {
                        mComposeListener.onFailed(exception);
                    }
                    return;
                }
                final int videoRotate = getVideoRotation(fd);
                final VideoSize srcVideoSize = getVideoSize(fd);
                mProcessParams.setSrcVideoSize(srcVideoSize);

                Mp4ComposerEngine engine = new Mp4ComposerEngine();
                engine.setProgressCallback(new Mp4ComposerEngine.ComposeProgressCallback() {
                    @Override
                    public void onProgress(final double progress) {
                        if (mComposeListener != null) {
                            mComposeListener.onProgress(progress);
                        }
                    }

                    @Override
                    public void onCompleted() {
                        if (mComposeListener != null) {
                            mComposeListener.onCompleted();
                        }
                    }

                    @Override
                    public void onFailed(VideoCustomException e) {
                        if (mComposeListener != null) {
                            mComposeListener.onFailed(e);
                        }
                    }
                });
                engine.setDataSource(fd);
                if (mProcessParams.mFilter == null) {
                    mProcessParams.setFilter(new GlFilter());
                }

                if (mProcessParams.mFillMode == null) {
                    mProcessParams.setFillMode(FillMode.PRESERVE_ASPECT_FIT);
                }

                if (mProcessParams.mCustomFillMode != null) {
                    mProcessParams.setFillMode(FillMode.CUSTOM);
                }

                if (mProcessParams.mDestVideoSize == null) {
                    if (mProcessParams.mFillMode == FillMode.CUSTOM) {
                        mProcessParams.setDestVideoSize(srcVideoSize);
                    } else {
                        Rotation rotate = Rotation.fromInt(mProcessParams.mRotateDegree + videoRotate);
                        if (rotate == Rotation.ROTATION_90 || rotate == Rotation.ROTATION_270) {
                            mProcessParams.setDestVideoSize(new VideoSize(srcVideoSize.mHeight, srcVideoSize.mWidth));
                        } else {
                            mProcessParams.setDestVideoSize(srcVideoSize);
                        }
                        mProcessParams.setRotateDegree(rotate.getRotation());
                    }
                }
                if (mProcessParams.mFilter instanceof IResolutionFilter) {
                    ((IResolutionFilter) mProcessParams.mFilter).setResolution(mProcessParams.mDestVideoSize);
                }

                if (mProcessParams.mTimeScale < 2) {
                    mProcessParams.setTimeScale(1);
                }

                LogUtils.d(TAG + ", rotation = " + (mProcessParams.mRotateDegree + videoRotate));
                LogUtils.d(TAG + ", inputResolution width = " + srcVideoSize.mWidth + " height = " + srcVideoSize.mHeight);
                LogUtils.d(TAG + ", outputResolution width = " + mProcessParams.mDestVideoSize.mWidth + " height = " + mProcessParams.mDestVideoSize.mHeight);
                LogUtils.d(TAG + ", fillMode = " + mProcessParams.mFillMode);

                try {
                    if (mProcessParams.mBitRate < 0) {
                        mProcessParams.setBitRate(calcBitRate(mProcessParams.mDestVideoSize.mWidth, mProcessParams.mDestVideoSize.mHeight));
                    }
                    engine.compose(mProcessParams);

                } catch (VideoCustomException e) {
                    e.printStackTrace();
                    if (mComposeListener != null) {
                        mComposeListener.onFailed(e);
                    }
                    return;
                }

                if (mComposeListener != null) {
                    mComposeListener.onCompleted();
                }
            }
        });

        return this;
    }

    public interface VideoComposeListener {

        void onProgress(double progress);

        void onCompleted();

        void onFailed(VideoCustomException e);

        void onCanceled();
    }

    private int getVideoRotation(FileDescriptor fd) {
        MediaMetadataRetriever retriever = new MediaMetadataRetriever();
        retriever.setDataSource(fd);
        String orientation = retriever.extractMetadata(
                MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION);
        retriever.release();
        return Integer.valueOf(orientation);
    }

    private int calcBitRate(int width, int height) {
        final int bitrate = (int) (0.25 * 30 * width * height);
        LogUtils.i(TAG + ", bitrate=" + bitrate);
        return bitrate;
    }

    private VideoSize getVideoSize(FileDescriptor fd) {
        MediaMetadataRetriever retriever = new MediaMetadataRetriever();
        retriever.setDataSource(fd);
        int width = Integer.valueOf(retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH));
        int height = Integer.valueOf(retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT));
        retriever.release();
        return new VideoSize(width, height);
    }

}
