package fm.jiecao.jcvideoplayer_lib;

import android.app.Activity;
import android.app.Dialog;
import android.content.Context;
import android.media.AudioManager;
import android.text.TextUtils;
import android.util.AttributeSet;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.SurfaceHolder;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.RelativeLayout;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import java.util.Timer;
import java.util.TimerTask;

/**
 * Manage MediaPlayer
 * Created by Nathen
 * On 2016/04/10 15:45
 */
public abstract class JCVideoPlayer extends FrameLayout implements View.OnClickListener, View.OnTouchListener, SeekBar.OnSeekBarChangeListener, SurfaceHolder.Callback, JCMediaManager.JCMediaPlayerListener {

  public static final String TAG = "JCVideoPlayer";
  public int CURRENT_STATE = -1;//-1相当于null
  public static final int CURRENT_STATE_PREPAREING = 0;
  public static final int CURRENT_STATE_PAUSE = 1;
  public static final int CURRENT_STATE_PLAYING = 2;
  public static final int CURRENT_STATE_OVER = 3;
  public static final int CURRENT_STATE_NORMAL = 4;
  public static final int CURRENT_STATE_ERROR = 5;
  private boolean touchingProgressBar = false;
  protected boolean IF_CURRENT_IS_FULLSCREEN = false;
  protected boolean IF_FULLSCREEN_IS_DIRECTLY = false;//IF_CURRENT_IS_FULLSCREEN should be true first
  private static boolean IF_FULLSCREEN_FROM_NORMAL = false;//to prevent infinite loop
  public static boolean IF_RELEASE_WHEN_ON_PAUSE = true;
  private boolean BACK_FROM_FULLSCREEN = false;
  private static long CLICK_QUIT_FULLSCREEN_TIME = 0;
  private static final int FULL_SCREEN_NORMAL_DELAY = 1000;

  protected ImageView ivStart;
  protected SeekBar skProgress;
  protected ImageView ivFullScreen;
  protected TextView tvTimeCurrent, tvTimeTotal;
  protected ViewGroup rlSurfaceContainer;

  protected ViewGroup llTopContainer, llBottomControl;

  protected JCResizeSurfaceView surfaceView;
  protected SurfaceHolder surfaceHolder;
  int surfaceId;// for onClick()

  protected String url;
  protected Object[] objects;

  private static Timer mUpdateProgressTimer;
  private static JCBuriedPoint JC_BURIED_POINT;
  protected int screenWidth;
  protected int screenHeight;
  private AudioManager mAudioManager;

  protected int threshold = 80;
  protected float downX;
  protected float downY;
  protected boolean changeVolume = false;
  protected boolean changePosition = false;
  protected int downPosition;
  protected int downVolume;

  protected Dialog dialogProgress;
  protected ProgressBar dpPb;
  protected TextView dpTvCurrent;
  protected TextView dpTvTotal;
  protected ImageView dpIv;
  protected int resultTimePosition;//change postion when finger up

  Dialog dialogVolum;
  ProgressBar dvProgressBar;

  public JCVideoPlayer(Context context) {
    super(context);
    init(context);
  }

  public JCVideoPlayer(Context context, AttributeSet attrs) {
    super(context, attrs);
    init(context);
  }

  protected void init(Context context) {
    View.inflate(context, getLayoutId(), this);
    ivStart = (ImageView) findViewById(R.id.start);
    ivFullScreen = (ImageView) findViewById(R.id.fullscreen);
    skProgress = (SeekBar) findViewById(R.id.progress);
    tvTimeCurrent = (TextView) findViewById(R.id.current);
    tvTimeTotal = (TextView) findViewById(R.id.total);
    llBottomControl = (ViewGroup) findViewById(R.id.layout_bottom);
    rlSurfaceContainer = (RelativeLayout) findViewById(R.id.surface_container);
    llTopContainer = (ViewGroup) findViewById(R.id.layout_top);
    surfaceView = (JCResizeSurfaceView) this.findViewById(R.id.surfaceView);
    surfaceHolder = surfaceView.getHolder();
    surfaceHolder.addCallback(this);

    ivStart.setOnClickListener(this);
    ivFullScreen.setOnClickListener(this);
    skProgress.setOnSeekBarChangeListener(this);
    llBottomControl.setOnClickListener(this);
    rlSurfaceContainer.setOnClickListener(this);
    skProgress.setOnTouchListener(this);

    rlSurfaceContainer.setOnTouchListener(this);
    screenWidth = getContext().getResources().getDisplayMetrics().widthPixels;
    screenHeight = getContext().getResources().getDisplayMetrics().heightPixels;
    mAudioManager = (AudioManager) getContext().getSystemService(Context.AUDIO_SERVICE);
  }

  public abstract int getLayoutId();

  protected static void setJcBuriedPoint(JCBuriedPoint jcBuriedPoint) {
    JC_BURIED_POINT = jcBuriedPoint;
  }

  public void setUp(String url, Object... objects) {
    if (JCMediaManager.intance().listener == this && (System.currentTimeMillis() - CLICK_QUIT_FULLSCREEN_TIME) < FULL_SCREEN_NORMAL_DELAY)
      return;
    CURRENT_STATE = CURRENT_STATE_NORMAL;
    this.url = url;
    this.objects = objects;
    setStateAndUi(CURRENT_STATE_NORMAL);
  }

  //set ui
  protected void setStateAndUi(int state) {
    CURRENT_STATE = state;
    switch (CURRENT_STATE) {
      case CURRENT_STATE_NORMAL:
        if (JCMediaManager.intance().listener == this) {
          JCMediaManager.intance().mediaPlayer.release();
        }
        break;
      case CURRENT_STATE_PREPAREING:
        break;
      case CURRENT_STATE_PLAYING:
        startProgressTimer();
        break;
      case CURRENT_STATE_PAUSE:
        startProgressTimer();
        break;
      case CURRENT_STATE_ERROR:
        JCMediaManager.intance().mediaPlayer.release();
        break;
    }
  }

  @Override
  public void onClick(View v) {
    int i = v.getId();
    if (i == R.id.start) {
      if (TextUtils.isEmpty(url)) {
        Toast.makeText(getContext(), "No url", Toast.LENGTH_SHORT).show();
        return;
      }
      if (CURRENT_STATE == CURRENT_STATE_NORMAL || CURRENT_STATE == CURRENT_STATE_ERROR) {
        if (JC_BURIED_POINT != null && CURRENT_STATE == CURRENT_STATE_NORMAL) {
          JC_BURIED_POINT.onClickStartIcon(url, objects);
        } else if (JC_BURIED_POINT != null) {
          JC_BURIED_POINT.onClickStartError(url, objects);
        }
        prepareVideo();
      } else if (CURRENT_STATE == CURRENT_STATE_PLAYING) {
        JCMediaManager.intance().mediaPlayer.pause();
        setStateAndUi(CURRENT_STATE_PAUSE);
        if (JC_BURIED_POINT != null && JCMediaManager.intance().listener == this) {
          if (IF_CURRENT_IS_FULLSCREEN) {
            JC_BURIED_POINT.onClickStopFullscreen(url, objects);
          } else {
            JC_BURIED_POINT.onClickStop(url, objects);
          }
        }
      } else if (CURRENT_STATE == CURRENT_STATE_PAUSE) {
        if (JC_BURIED_POINT != null && JCMediaManager.intance().listener == this) {
          if (IF_CURRENT_IS_FULLSCREEN) {
            JC_BURIED_POINT.onClickResumeFullscreen(url, objects);
          } else {
            JC_BURIED_POINT.onClickResume(url, objects);
          }
        }
        JCMediaManager.intance().mediaPlayer.start();
        setStateAndUi(CURRENT_STATE_PLAYING);
      }
    } else if (i == R.id.fullscreen) {
      if (IF_CURRENT_IS_FULLSCREEN) {
        //quit fullscreen
        backFullscreen();
      } else {
        if (JC_BURIED_POINT != null && JCMediaManager.intance().listener == this) {
          JC_BURIED_POINT.onEnterFullscreen(url, objects);
        }
        //to fullscreen
        JCMediaManager.intance().mediaPlayer.setDisplay(null);
        JCMediaManager.intance().lastListener = this;
        JCMediaManager.intance().listener = null;
        IF_FULLSCREEN_FROM_NORMAL = true;
        IF_RELEASE_WHEN_ON_PAUSE = false;
        JCFullScreenActivity.toActivityFromNormal(getContext(), CURRENT_STATE, url, JCVideoPlayer.this.getClass(), this.objects);
      }
    } else if (i == R.id.surface_container && CURRENT_STATE == CURRENT_STATE_ERROR) {
      if (JC_BURIED_POINT != null) {
        JC_BURIED_POINT.onClickStartError(url, objects);
      }
      prepareVideo();
    }
  }

  protected void prepareVideo() {
    if (JCMediaManager.intance().listener != null) {
      JCMediaManager.intance().listener.onCompletion();
    }
    JCMediaManager.intance().listener = this;
//        addSurfaceView();
    JCMediaManager.intance().prepareToPlay(getContext(), url);
    if (!IF_CURRENT_IS_FULLSCREEN) {
      setDisplayCaseFailse();
    }
    setStateAndUi(CURRENT_STATE_PREPAREING);
  }

  private void addSurfaceView() {
    if (rlSurfaceContainer.getChildCount() > 0) {
      rlSurfaceContainer.removeAllViews();
    }
    surfaceView = new JCResizeSurfaceView(getContext());
    surfaceId = surfaceView.getId();
    surfaceHolder = surfaceView.getHolder();
    surfaceHolder.addCallback(this);
//        surfaceView.setOnClickListener(this);
    RelativeLayout.LayoutParams layoutParams = new RelativeLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
    layoutParams.addRule(RelativeLayout.CENTER_IN_PARENT);
    rlSurfaceContainer.addView(surfaceView, layoutParams);
  }

  @Override
  public boolean onTouch(View v, MotionEvent event) {
    float x = event.getX();
    float y = event.getY();
    int id = v.getId();
    if (id == R.id.surface_container) {
      switch (event.getAction()) {
        case MotionEvent.ACTION_DOWN:
          touchingProgressBar = true;

          downX = x;
          downY = y;
          changeVolume = false;
          changePosition = false;
          /////////////////////
          cancelProgressTimer();
          break;
        case MotionEvent.ACTION_MOVE:
          float deltaX = x - downX;
          float deltaY = y - downY;
          float absDeltaX = Math.abs(deltaX);
          float absDeltaY = Math.abs(deltaY);
          if (IF_CURRENT_IS_FULLSCREEN) {
            if (!changePosition && !changeVolume) {
              if (absDeltaX > threshold || absDeltaY > threshold) {
                if (absDeltaX >= threshold) {
                  if (CURRENT_STATE == CURRENT_STATE_PLAYING || CURRENT_STATE == CURRENT_STATE_PAUSE) {
                    changePosition = true;
                    downPosition = JCMediaManager.intance().mediaPlayer.getCurrentPosition();
                    if (JC_BURIED_POINT != null && JCMediaManager.intance().listener == this) {
                      JC_BURIED_POINT.onTouchScreenSeekPosition(url, objects);
                    }
                  }
                } else {
                  changeVolume = true;
                  downVolume = mAudioManager.getStreamVolume(AudioManager.STREAM_MUSIC);
                  if (JC_BURIED_POINT != null && JCMediaManager.intance().listener == this) {
                    JC_BURIED_POINT.onTouchScreenSeekVolume(url, objects);
                  }
                }
              }
            }
          }
          if (changePosition) {
            showProgressDialog(deltaX);
          }
          if (changeVolume) {
            showVolumDialog(-deltaY);
          }

          break;
        case MotionEvent.ACTION_UP:
          touchingProgressBar = false;
          if (dialogProgress != null) {
            dialogProgress.dismiss();
          }
          if (dialogVolum != null) {
            dialogVolum.dismiss();
          }
          if (changePosition) {
            JCMediaManager.intance().mediaPlayer.seekTo(resultTimePosition);
            int duration = JCMediaManager.intance().mediaPlayer.getDuration();
            int progress = resultTimePosition * 100 / (duration == 0 ? 1 : duration);
            skProgress.setProgress(progress);
          }
          /////////////////////
          startProgressTimer();
          if (JC_BURIED_POINT != null && JCMediaManager.intance().listener == this) {
            if (IF_CURRENT_IS_FULLSCREEN) {
              JC_BURIED_POINT.onClickSeekbarFullscreen(url, objects);
            } else {
              JC_BURIED_POINT.onClickSeekbar(url, objects);
            }
          }
          break;
      }
    }

    return false;
  }

  private void showProgressDialog(float deltaX) {
    if (dialogProgress == null) {
      View localView = LayoutInflater.from(getContext()).inflate(fm.jiecao.jcvideoplayer_lib.R.layout.jc_progress_dialog, null);
      dpPb = ((ProgressBar) localView.findViewById(fm.jiecao.jcvideoplayer_lib.R.id.duration_progressbar));
      dpTvCurrent = ((TextView) localView.findViewById(fm.jiecao.jcvideoplayer_lib.R.id.tv_current));
      dpTvTotal = ((TextView) localView.findViewById(fm.jiecao.jcvideoplayer_lib.R.id.tv_duration));
      dpIv = ((ImageView) localView.findViewById(fm.jiecao.jcvideoplayer_lib.R.id.duration_image_tip));
      dialogProgress = new Dialog(getContext(), fm.jiecao.jcvideoplayer_lib.R.style.jc_style_dialog_progress);
      dialogProgress.setContentView(localView);
      dialogProgress.getWindow().addFlags(Window.FEATURE_ACTION_BAR);
      dialogProgress.getWindow().addFlags(32);
      dialogProgress.getWindow().addFlags(16);
      dialogProgress.getWindow().setLayout(-2, -2);
      WindowManager.LayoutParams localLayoutParams = dialogProgress.getWindow().getAttributes();
      localLayoutParams.gravity = 49;
      localLayoutParams.y = getResources().getDimensionPixelOffset(fm.jiecao.jcvideoplayer_lib.R.dimen.jc_progress_dialog_margin_top);
      dialogProgress.getWindow().setAttributes(localLayoutParams);
    }
    if (!dialogProgress.isShowing()) {
      dialogProgress.show();
    }
    int totalTime = JCMediaManager.intance().mediaPlayer.getDuration();
    resultTimePosition = (int) (downPosition + deltaX * totalTime / screenWidth);
    dpTvCurrent.setText(JCUtils.stringForTime(resultTimePosition));
    dpTvTotal.setText(" / " + JCUtils.stringForTime(totalTime) + "");
    dpPb.setProgress(resultTimePosition * 100 / totalTime);
    if (deltaX > 0) {
      dpIv.setBackgroundResource(R.drawable.jc_forward_icon);
    } else {
      dpIv.setBackgroundResource(R.drawable.jc_backward_icon);
    }
  }

  private void showVolumDialog(float deltaY) {
    if (dialogVolum == null) {
      View localView = LayoutInflater.from(getContext()).inflate(R.layout.jc_volume_dialog, null);
      dvProgressBar = ((ProgressBar) localView.findViewById(R.id.volume_progressbar));
      dialogVolum = new Dialog(getContext(), R.style.jc_style_dialog_progress);
      dialogVolum.setContentView(localView);
      dialogVolum.getWindow().addFlags(8);
      dialogVolum.getWindow().addFlags(32);
      dialogVolum.getWindow().addFlags(16);
      dialogVolum.getWindow().setLayout(-2, -2);
      WindowManager.LayoutParams localLayoutParams = dialogVolum.getWindow().getAttributes();
      localLayoutParams.gravity = 19;
      localLayoutParams.x = getContext().getResources().getDimensionPixelOffset(R.dimen.jc_volume_dialog_margin_left);
      dialogVolum.getWindow().setAttributes(localLayoutParams);
    }
    if (!dialogVolum.isShowing()) {
      dialogVolum.show();
    }
    int max = mAudioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC);
    int deltaV = (int) (max * deltaY * 3 / screenHeight);
    mAudioManager.setStreamVolume(AudioManager.STREAM_MUSIC, downVolume + deltaV, 0);
    int transformatVolume = (int) (downVolume * 100 / max + deltaY * 3 * 100 / screenHeight);
    dvProgressBar.setProgress(transformatVolume);
  }

  @Override
  public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
    if (fromUser) {
      int time = progress * JCMediaManager.intance().mediaPlayer.getDuration() / 100;
      JCMediaManager.intance().mediaPlayer.seekTo(time);
    }
  }

  @Override
  public void onStartTrackingTouch(SeekBar seekBar) {

  }

  @Override
  public void onStopTrackingTouch(SeekBar seekBar) {

  }

  @Override
  public void surfaceCreated(SurfaceHolder holder) {
    if (FalseSetDisPlay) {//case setDisplay faild in prepared();
      FalseSetDisPlay = false;
      setDisplayCaseFailse();
    }
    if (IF_CURRENT_IS_FULLSCREEN) {//fullscreen from normal
      setDisplayCaseFailse();
    }
    if (BACK_FROM_FULLSCREEN) {
      BACK_FROM_FULLSCREEN = false;
      setDisplayCaseFailse();
    }
    ifNeedCreateSurfaceView = false;
  }

  private boolean FalseSetDisPlay = false;

  private void setDisplayCaseFailse() {
    try {
      JCMediaManager.intance().mediaPlayer.setDisplay(surfaceHolder);
    } catch (IllegalArgumentException e) {
      Log.i(TAG, "recreate surfaceview from IllegalArgumentException");
      FalseSetDisPlay = true;
      addSurfaceView();
      e.printStackTrace();
    } catch (IllegalStateException e1) {
      Log.i(TAG, "recreate surfaceview from IllegalStateException");
      FalseSetDisPlay = true;
      addSurfaceView();
      e1.printStackTrace();
    }
  }

  @Override
  public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {
  }

  @Override
  public void surfaceDestroyed(SurfaceHolder holder) {
    ifNeedCreateSurfaceView = true;
  }

  private boolean ifNeedCreateSurfaceView = false;

  @Override
  public void onPrepared() {
    if (CURRENT_STATE != CURRENT_STATE_PREPAREING) return;
    JCMediaManager.intance().mediaPlayer.start();
    startProgressTimer();
    setStateAndUi(CURRENT_STATE_PLAYING);
  }

  @Override
  public void onAutoCompletion() {
    //make me normal first
    if (JC_BURIED_POINT != null && JCMediaManager.intance().listener == this) {
      if (IF_CURRENT_IS_FULLSCREEN) {
        JC_BURIED_POINT.onAutoCompleteFullscreen(url, objects);
      } else {
        JC_BURIED_POINT.onAutoComplete(url, objects);
      }
    }
    onCompletion();
  }

  @Override
  public void onCompletion() {
    //make me normal first
    cancelProgressTimer();
    resetProgressAndTime();
    setStateAndUi(CURRENT_STATE_NORMAL);

    //if fullscreen finish activity what ever the activity is directly or click fullscreen
    finishMyFullscreen();

    if (IF_FULLSCREEN_FROM_NORMAL) {//如果在进入全屏后播放完就初始化自己非全屏的控件
      IF_FULLSCREEN_FROM_NORMAL = false;
      JCMediaManager.intance().lastListener.onCompletion();
    }
  }

  @Override
  public void onBufferingUpdate(int percent) {
    if (CURRENT_STATE != CURRENT_STATE_NORMAL && CURRENT_STATE != CURRENT_STATE_PREPAREING) {
      setTextAndProgress(percent);
    }
  }

  @Override
  public void onSeekComplete() {

  }

  @Override
  public void onError(int what, int extra) {
    if (what != 38) {
      setStateAndUi(CURRENT_STATE_ERROR);
    }
  }

  @Override
  public void onVideoSizeChanged() {
    int mVideoWidth = JCMediaManager.intance().currentVideoWidth;
    int mVideoHeight = JCMediaManager.intance().currentVideoHeight;
    if (mVideoWidth != 0 && mVideoHeight != 0) {
      surfaceHolder.setFixedSize(mVideoWidth, mVideoHeight);
      surfaceView.requestLayout();
    }
  }

  @Override
  public void onBackFullscreen() {
    CURRENT_STATE = JCMediaManager.intance().lastState;
    BACK_FROM_FULLSCREEN = true;
    setStateAndUi(CURRENT_STATE);
  }

  protected void startProgressTimer() {
    cancelProgressTimer();
    mUpdateProgressTimer = new Timer();
    mUpdateProgressTimer.schedule(new TimerTask() {
      @Override
      public void run() {
        if (getContext() != null && getContext() instanceof Activity) {
          ((Activity) getContext()).runOnUiThread(new Runnable() {
            @Override
            public void run() {
              if (CURRENT_STATE == CURRENT_STATE_PLAYING) {
                setTextAndProgress(0);
              }
            }
          });
        }
      }
    }, 0, 300);
  }

  protected void cancelProgressTimer() {
    if (mUpdateProgressTimer != null) {
      mUpdateProgressTimer.cancel();
    }
  }

  protected void setTextAndProgress(int secProgress) {
    int position = JCMediaManager.intance().mediaPlayer.getCurrentPosition();
    int duration = JCMediaManager.intance().mediaPlayer.getDuration();
    // if duration == 0 (e.g. in HLS streams) avoids ArithmeticException
    int progress = position * 100 / (duration == 0 ? 1 : duration);
    setProgressAndTime(progress, secProgress, position, duration);
  }

  protected void setProgressAndTime(int progress, int secProgress, int currentTime, int totalTime) {
    if (!touchingProgressBar) {
      if (progress != 0) skProgress.setProgress(progress);
    }
    if (secProgress != 0) skProgress.setSecondaryProgress(secProgress);
    tvTimeCurrent.setText(JCUtils.stringForTime(currentTime));
    tvTimeTotal.setText(JCUtils.stringForTime(totalTime));
  }

  protected void resetProgressAndTime() {
    skProgress.setProgress(0);
    skProgress.setSecondaryProgress(0);
    tvTimeCurrent.setText(JCUtils.stringForTime(0));
    tvTimeTotal.setText(JCUtils.stringForTime(0));
  }

  protected void quitFullScreenGoToNormal() {
    if (JC_BURIED_POINT != null && JCMediaManager.intance().listener == this) {
      JC_BURIED_POINT.onQuitFullscreen(url, objects);
    }
    JCMediaManager.intance().mediaPlayer.setDisplay(null);
    JCMediaManager.intance().listener = JCMediaManager.intance().lastListener;
    JCMediaManager.intance().lastState = CURRENT_STATE;//save state
    JCMediaManager.intance().listener.onBackFullscreen();
    finishMyFullscreen();
  }

  protected void finishMyFullscreen() {
    if (getContext() instanceof JCFullScreenActivity) {
      ((JCFullScreenActivity) getContext()).finish();
    }
  }

  public void backFullscreen() {
    if (IF_FULLSCREEN_IS_DIRECTLY) {
      JCMediaManager.intance().mediaPlayer.stop();
      finishMyFullscreen();
    } else {
      CLICK_QUIT_FULLSCREEN_TIME = System.currentTimeMillis();
      IF_RELEASE_WHEN_ON_PAUSE = false;
      quitFullScreenGoToNormal();
    }
  }

  public static void releaseAllVideos() {
    if (IF_RELEASE_WHEN_ON_PAUSE) {
      JCMediaManager.intance().mediaPlayer.release();
      if (JCMediaManager.intance().listener != null) {
        JCMediaManager.intance().listener.onCompletion();
      }
    } else {
      IF_RELEASE_WHEN_ON_PAUSE = true;
    }
  }
}
