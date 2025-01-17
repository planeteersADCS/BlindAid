package com.planeteers.aivision.obstacle;

import android.app.Activity;
import android.graphics.Typeface;
import android.hardware.Sensor;
import android.hardware.SensorManager;
import android.media.AudioManager;
import android.media.ToneGenerator;
import android.os.Bundle;
import android.os.Vibrator;
import android.text.Html;
import android.widget.TextView;

import com.planeteers.aivision.R;
import com.planeteers.aivision.base.TalkActivity;
import com.planeteers.aivision.view.BlindViewUtil;

import org.opencv.android.BaseLoaderCallback;
import org.opencv.android.CameraBridgeViewBase;
import org.opencv.android.JavaCameraView;
import org.opencv.android.LoaderCallbackInterface;
import org.opencv.android.OpenCVLoader;
import org.opencv.core.Core;
import org.opencv.core.Mat;
import org.opencv.core.MatOfPoint;
import org.opencv.core.Scalar;
import org.opencv.imgproc.Imgproc;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import butterknife.Bind;
import butterknife.ButterKnife;
import timber.log.Timber;

/**
 * Created by flavius on 11/10/15.
 */
public class ObstacleDetection extends TalkActivity implements CameraBridgeViewBase.CvCameraViewListener2, Orientation.Listener {
    public final static String INSTRUCTIONS = "Swipe down to go back.";
    private static final String TAG = "ObstacleDetection";
    private static final Scalar CONTOUR_COLOR = new Scalar(255, 0, 0, 255);
    private static final int DELAY_NORMAL = 2000;
    private static final int DELAY_FAST = 800;
    private static final int DELAY_FASTER = 400;
    private static final int DELAY_FASTEST = 260;
    private static final int TONE_NORMAL = 10;
    private static final int TONE_HIGH = 8;
    private static final int TONE_HIGHER = 6;
    private static final int MIN_ROTATE_DOWN_MESSAGE_DELAY = 3000;
    @Bind(R.id.tag_textview)
    TextView tagTextView;
    private JavaCameraView mCameraView;
    private Mat mGray;
    private Mat mColor;
    private Mat mRotated;
    private Mat mBlurred;
    private Mat mDestination;
    private Mat mHierarchy;
    private List<MatOfPoint> mContours;
    private Mat mMat;
    private Vibrator mVibrator;
    private SensorManager mSensorManager;
    private Sensor mSensor;
    private Orientation mOrientation;
    private ToneGenerator mToneGenerator;
    private long mLastTimestamp;
    private BaseLoaderCallback mLoaderCallback = new BaseLoaderCallback(this) {
        @Override
        public void onManagerConnected(int status) {
            switch (status) {
                case LoaderCallbackInterface.SUCCESS: {
                    Timber.i("OpenCV loaded successfully");
                    mCameraView.enableView();
                    mCameraView.setCvCameraViewListener(ObstacleDetection.this);
                }
                break;
                default: {
                    super.onManagerConnected(status);
                }
                break;
            }
        }
    };
    private int mDelay;
    private long mLastRotateDownMessage;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_obstacle);

        ButterKnife.bind(this);

        mCameraView = (JavaCameraView) findViewById(R.id.HelloOpenCvView);
        mVibrator = (Vibrator) getSystemService(VIBRATOR_SERVICE);

        mOrientation = new Orientation((SensorManager) getSystemService(Activity.SENSOR_SERVICE),
                getWindow().getWindowManager());

        mToneGenerator = new ToneGenerator(AudioManager.STREAM_MUSIC, ToneGenerator.MAX_VOLUME);


        //UI stuff
        Typeface typeface = Typeface.createFromAsset(getAssets(), "fonts/robotoslab_light.ttf");
        tagTextView.setTypeface(typeface);

        String htmlInstructions = INSTRUCTIONS.replace(".", ".<br/> <br/>");
        tagTextView.setText(Html.fromHtml(htmlInstructions));

        tagTextView.setContentDescription(INSTRUCTIONS);

    }

    @Override
    protected void onPostCreate(Bundle savedInstanceState) {
        super.onPostCreate(savedInstanceState);

        BlindViewUtil util = new BlindViewUtil(new BlindViewUtil.BlindNavGestureListener() {
            @Override
            public boolean onSwipeLeft() {
                return false;
            }

            @Override
            public boolean onSwipeRight() {
                return false;
            }

            @Override
            public boolean onSwipeUp() {
                return false;
            }

            @Override
            public boolean onSwipeDown() {
                Timber.d("OnSwipeDown Called");
                finish();
                return true;
            }

            @Override
            public boolean onClick() {
                return false;
            }
        });

        mCameraView.setOnTouchListener(util.blindTouchListener);

        talkBack(INSTRUCTIONS);
    }

    @Override
    public void onResume() {
        super.onResume();
        OpenCVLoader.initAsync(OpenCVLoader.OPENCV_VERSION_3_0_0, this, mLoaderCallback);
        mLastTimestamp = System.currentTimeMillis();
        mLastRotateDownMessage = System.currentTimeMillis();
        mOrientation.startListening(this);
        mDelay = DELAY_NORMAL;
    }

    @Override
    public void onPause() {
        super.onPause();
        mOrientation.stopListening();
    }

    @Override
    public void onCameraViewStarted(int width, int height) {

    }

    @Override
    public void onCameraViewStopped() {

    }

    @Override
    public Mat onCameraFrame(CameraBridgeViewBase.CvCameraViewFrame inputFrame) {
        mGray = inputFrame.gray();
        mColor = inputFrame.rgba();

//        Log.d("Sizes", "" + mGray.type()); // writes 0
//        Log.d("Sizes", "" + mColor.type()); // writes 24
//        Log.d("Sizes", "" + mColor.size().toString() + "; " + mGray.size().toString());//1024x768

//        MatOfKeyPoint matOfKeyPoint = new MatOfKeyPoint();
//        FeatureDetector detector = FeatureDetector.create(FeatureDetector.FAST);
//        detector.detect(gray, matOfKeyPoint);
//        Mat out = new Mat();
//        Features2d.drawKeypoints(gray, matOfKeyPoint, out);

        if (mBlurred == null) {
            mBlurred = new Mat();
        }

        Imgproc.medianBlur(mGray, mBlurred, 13);

        if (mDestination == null) {
            mDestination = new Mat();
        }

        Imgproc.threshold(mBlurred, mDestination, 170, 255, Imgproc.THRESH_BINARY);

        if (mRotated == null) {
            mRotated = new Mat();
        }

        Core.flip(mDestination.t(), mRotated, 1); //rotate 90° counter-clockwise
        Imgproc.resize(mRotated, mRotated, mColor.size());

        if (mContours == null) {
            mContours = new ArrayList<>();
        } else {
            mContours.clear();
        }

        if (mHierarchy == null) {
            mHierarchy = new Mat();
        }

        if (mMat == null) {
            mMat = new Mat();
        }
        mMat = mRotated.clone();
//
        Imgproc.findContours(mRotated, mContours, mHierarchy, Imgproc.RETR_TREE, Imgproc.CHAIN_APPROX_SIMPLE);
//        Imgproc.drawContours(mMat, mContours, -1, CONTOUR_COLOR);

        double maxArea = 0.0;
        Iterator<MatOfPoint> each = mContours.iterator();
        while (each.hasNext()) {
            MatOfPoint wrapper = each.next();
            double area = Imgproc.contourArea(wrapper);
            if (area > maxArea)
                maxArea = area;
        }

        if (maxArea > 70000) {
            mVibrator.vibrate(200);
        }
        Timber.d("Area", "" + maxArea);
        return mMat;
    }

    @Override
    public void onOrientationChanged(float pitch, float roll) {
        Timber.d("Orient", "pitch: " + pitch + "   roll: " + roll);
        final long now = System.currentTimeMillis();

        if (now - mLastTimestamp > mDelay) {
            mLastTimestamp = now;

            int tone, duration = 100;
            if (pitch < -60) {
                tone = TONE_NORMAL;
                mDelay = DELAY_NORMAL;
            } else {
                tone = TONE_NORMAL;
                mDelay = (int) Math.abs(pitch) * 15;
                Timber.d("Delay", "" + mDelay);
                mDelay = Math.max(DELAY_FASTEST, mDelay);
            }

            if (pitch > -30) {
                if (now - mLastRotateDownMessage > MIN_ROTATE_DOWN_MESSAGE_DELAY) {
                    talkBack("Rotate device downward");
                    mLastRotateDownMessage = now;
                }
            }
            mToneGenerator.startTone(tone, duration);
        }
    }
}