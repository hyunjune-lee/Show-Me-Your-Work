package lunchgym.first.showmeyourwork;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import android.app.AlertDialog;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.ImageFormat;
import android.graphics.Matrix;
import android.graphics.Rect;
import android.graphics.SurfaceTexture;
import android.graphics.YuvImage;
import android.media.Image;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;
import android.webkit.JavascriptInterface;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.Toast;

import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.ar.core.Anchor;
import com.google.ar.core.AugmentedImage;
import com.google.ar.core.Frame;
import com.google.ar.core.HitResult;
import com.google.ar.core.Plane;
import com.google.ar.core.TrackingState;
import com.google.ar.core.exceptions.NotYetAvailableException;
import com.google.ar.core.exceptions.ResourceExhaustedException;
import com.google.ar.sceneform.AnchorNode;
import com.google.ar.sceneform.FrameTime;
import com.google.ar.sceneform.Node;
import com.google.ar.sceneform.Scene;
import com.google.ar.sceneform.math.Vector3;
import com.google.ar.sceneform.rendering.Color;
import com.google.ar.sceneform.rendering.ExternalTexture;
import com.google.ar.sceneform.rendering.ModelRenderable;
import com.google.firebase.ml.vision.FirebaseVision;
import com.google.firebase.ml.vision.cloud.FirebaseVisionCloudDetectorOptions;
import com.google.firebase.ml.vision.common.FirebaseVisionImage;
import com.google.firebase.ml.vision.text.FirebaseVisionCloudTextRecognizerOptions;
import com.google.firebase.ml.vision.text.FirebaseVisionText;
import com.google.firebase.ml.vision.text.FirebaseVisionTextRecognizer;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.select.Elements;
import org.opencv.android.CameraBridgeViewBase;
import org.opencv.core.Mat;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.Collection;
import java.util.Objects;

import dmax.dialog.SpotsDialog;


/*public class MainActivity extends AppCompatActivity
        implements CameraBridgeViewBase.CvCameraViewListener2 {*/

public class MainActivity extends AppCompatActivity {

    private static final String TAG = "opencv";

    //Crawling ?????? ?????? ==========================================================================

    private String htmlPageUrl ="https://search.naver.com/search.naver?where=video&sm=tab_jum&query="; //????????? ??????????????? URL??????
    private String cardinal;
    private String name;
    private WebView fakeWb;
    private String source = "";
    private String videoSrc;
    private String playUrl = "";
    private JsoupAsyncTask jsoupAsyncTask;
    private WebviewAsyncTask webViewAsyncTask;



    //OpenCV ?????? ??????===============================================================================
    private Mat matInput;
    private Mat matResult;

    private CameraBridgeViewBase mOpenCvCameraView;

    public native void FindMemberNameInPaper(long matAddrInput, long matAddrResult);


    static {
        System.loadLibrary("opencv_java4");
        System.loadLibrary("native-lib");
    }


    //????????? ?????????
    private ImageView ivTest;

    //==============================================================================================

    //AR ?????? ??????==================================================================================
    private ExternalTexture texture;
    private MediaPlayer mediaPlayer;
    private CustomArFragment arFragment;
    private Scene scene;
    private ModelRenderable renderable;
    private boolean isImageDetected = false;


    // Controls the height of the video in world space.
    private static final float VIDEO_HEIGHT_METERS = 0.85f;

    //==============================================================================================


    //?????????????????? OCR ?????? ??????====================================================================
    AlertDialog waitingDialog;
    Button btnCapture;
    Image imageCapture;

    //????????? ????????????
    Matrix rotatedMatrix;



    //==============================================================================================

    //????????? ?????? ??????==============================================================================


    //==============================================================================================


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        //crawling ?????? ??? fake webview ??????==========================================================================
        fakeWb = findViewById(R.id.wb_fake);
        fakeWb.setVisibility(View.INVISIBLE);

        //Webview ?????????????????? ?????????
        fakeWb.getSettings().setJavaScriptEnabled(true);
        //?????????????????? ??????????????? ??????
        fakeWb.addJavascriptInterface(new MyJavascriptInterface(), "Android");
        fakeWb.setWebViewClient(new WebViewClient() {
            @Override
            public void onPageFinished(WebView view, String url) {
                super.onPageFinished(view, url);
                //?????????????????? ?????????????????? ?????????????????? getHtml??? ??????
                //?????????????????? ?????? ???????????? html????????? ????????? ???????????? ????????? ?????????
                view.loadUrl("javascript:window.Android.getHtml(document.getElementsByTagName('body')[0].innerHTML);");
            }
        });






        //?????????????????? OCR==========================================================================
        //?????????(?????? ?????????)
        ivTest = findViewById(R.id.iv_test);



        //?????? ???????????????
        waitingDialog = new SpotsDialog.Builder()
                .setCancelable(false)
                .setMessage("????????? ??????????????????")
                .setContext(this)
                .build();

        //????????????
        btnCapture = findViewById(R.id.btn_capture);
        btnCapture.setOnClickListener(v -> {

            try {
                // ar fragment ?????? ????????? ????????????
                // ?????? opencv ??? ???????????? opencv ?????? ????????? ???????????? ar fragment ?????? ?????????????????????
                imageCapture = Objects.requireNonNull(arFragment.getArSceneView().getArFrame()).acquireCameraImage();


                //?????? ??????????????? ????????????
                waitingDialog.show();

                //????????? ????????? ?????????(???????????? ??????????????? ?????????????????? ??????)
                byte[] byteArray = imageToByte(imageCapture);

                //?????? ?????????
                Bitmap bitmapCapture = BitmapFactory.decodeByteArray(byteArray, 0, byteArray.length, null);

                //90??? ?????????????????? ?????? ????????? ????????????
                //??? ???????????? ?????? ??????????????? ????????? ???????????? ????????? ???????????? ??????????????? ??????)
                //?????????????????? ????????? ?????? ????????? ??????
                rotatedMatrix = new Matrix();
                rotatedMatrix.postRotate(90);
                bitmapCapture = Bitmap.createBitmap(bitmapCapture, 0, 0, bitmapCapture.getWidth(), bitmapCapture.getHeight(), rotatedMatrix, true);


                ivTest.setImageBitmap(bitmapCapture);

                //?????????????????? ?????? ??????
                recognizeText(bitmapCapture);

            } catch (NotYetAvailableException e  ) {
                e.printStackTrace();
                Log.w(TAG, "OCR - onCreate: btnCapture NotYetAvailableException ");
            }
            catch (ResourceExhaustedException e ){
                e.printStackTrace();
                Log.w(TAG, "OCR - onCreate: btnCapture ResourceExhaustedException ");


            }







        });

        //AR========================================================================================
        // Create an ExternalTexture for displaying the contents of the video.
        texture = new ExternalTexture();
        mediaPlayer = new MediaPlayer();
//        mediaPlayer = MediaPlayer.create(this, Uri.parse("https://serviceapi.nmv.naver.com/view/ugcPlayer.nhn?vid=7DCA747C80C640305145C42E13B6329C4660&inKey=V1268b72f809c30d1ef02c87c44d03bf0878269db9d3b7e681252ba8028ec7566512ec87c44d03bf08782&wmode=opaque&hasLink=1&autoPlay=false&beginTime=0"));
//
//        mediaPlayer = MediaPlayer.create(this, R.raw.test_video);
        try {


            mediaPlayer.setDataSource("https://media.fmkorea.com/files/attach/new/20191101/3655109/2089104173/2339677357/1cb879a979e99e75f598d2e9038bfa4e.gif.mp4?d");

            mediaPlayer.prepare();
        } catch (IOException e) {
            e.printStackTrace();
            Log.w(TAG, "mediaPlayer.setDataSource: fail" );
    }



        mediaPlayer.setSurface(texture.getSurface());
        mediaPlayer.setLooping(false);

        ModelRenderable
                .builder()
                .setSource(this, Uri.parse("video_screen.sfb"))
                .build()
                .thenAccept(modelRenderable -> {
                    modelRenderable.getMaterial().setExternalTexture("videoTexture",
                            texture);
                    modelRenderable.getMaterial().setFloat4("keyColor",
                            new Color(0.01843f, 1f, 0.098f));

                    renderable = modelRenderable;
                });



        arFragment = (CustomArFragment)
                getSupportFragmentManager().findFragmentById(R.id.ar_fragment);

        scene = arFragment.getArSceneView().getScene();

        scene.addOnUpdateListener(this::onUpdate);


        //?????? ???????????? AR ????????? ????????????----------------------------------------------------------
        //*?????? chromakey video
        arFragment.setOnTapArPlaneListener(
                (HitResult hitResult, Plane plane, MotionEvent motionEvent) -> {
                    if (renderable == null) {
                        return;
                    }

                    // Create the Anchor.
                    Anchor anchor = hitResult.createAnchor();
                    AnchorNode anchorNode = new AnchorNode(anchor);
                    anchorNode.setParent(arFragment.getArSceneView().getScene());

                    // Create a node to render the video and add it to the anchor.
                    Node videoNode = new Node();
                    videoNode.setParent(anchorNode);

                    // Set the scale of the node so that the aspect ratio of the video is correct.
                    float videoWidth = mediaPlayer.getVideoWidth();
                    float videoHeight = mediaPlayer.getVideoHeight();
                    videoNode.setLocalScale(
                            new Vector3(
                                    VIDEO_HEIGHT_METERS * (videoWidth / videoHeight), VIDEO_HEIGHT_METERS, 1.0f));

//                    Toast.makeText(getApplicationContext()  , "videoWidth " + videoWidth + "\n videoHeight" +videoHeight, Toast.LENGTH_LONG).show();



                    // Start playing the video when the first node is placed.
                    if (!mediaPlayer.isPlaying()) {
                        mediaPlayer.start();

                        // Wait to set the renderable until the first frame of the  video becomes available.
                        // This prevents the renderable from briefly appearing as a black quad before the video
                        // plays.
                        texture
                                .getSurfaceTexture()
                                .setOnFrameAvailableListener(
                                        (SurfaceTexture surfaceTexture) -> {
                                            videoNode.setRenderable(renderable);
                                            texture.getSurfaceTexture().setOnFrameAvailableListener(null);
                                        });
                    } else {
                        videoNode.setRenderable(renderable);
                    }
                });








        //------------------------------------------------------------------------------------------







        //==========================================================================================

        //OpenCV====================================================================================

        /*getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN,
                WindowManager.LayoutParams.FLAG_FULLSCREEN);
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON,
                WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);


        mOpenCvCameraView = (CameraBridgeViewBase)findViewById(R.id.activity_surface_view);
        mOpenCvCameraView.setVisibility(SurfaceView.VISIBLE);
        mOpenCvCameraView.setCvCameraViewListener(this);
        mOpenCvCameraView.setCameraIndex(0); // front-camera(1),  back-camera(0)*/
        //===========================================================================================

    }


    //?????????????????? OCR==============================================================================

    //????????????????????? ?????? ???????????? ?????? ??????
    private void recognizeText(Bitmap bitmapCapture) {
        FirebaseVisionImage image = FirebaseVisionImage.fromBitmap(bitmapCapture);

        //?????? ????????? => ????????????.. (ko ????????? ????????? ????????? ??????????????? ????????????)
        FirebaseVisionCloudTextRecognizerOptions options =
                new FirebaseVisionCloudTextRecognizerOptions.Builder().setLanguageHints(Arrays.asList("ko"))
                .build();

        FirebaseVisionTextRecognizer textRecognizer = FirebaseVision.getInstance()
                .getCloudTextRecognizer(options);

        textRecognizer.processImage(image)
                .addOnSuccessListener(new OnSuccessListener<FirebaseVisionText>() {
                    @Override
                    public void onSuccess(FirebaseVisionText firebaseVisionText) {
//                        Log.w(TAG, "OCR -  onSuccess: "+firebaseVisionText.getText() );

                        Log.w(TAG, "OCR -  onSuccess: "+firebaseVisionText.getTextBlocks().get(0).getText() );

                        try{
                            String inputText = firebaseVisionText.getTextBlocks().get(0).getText();

                            if(inputText.length()>0){
                                cardinal = inputText.substring(0, 1);
                                name = inputText.substring(3,6);
                            }
                            Toast.makeText(MainActivity.this, cardinal+"??? "+name, Toast.LENGTH_SHORT).show();
                            htmlPageUrl = htmlPageUrl+cardinal+"???%20"+name;

//                            Intent intent = new Intent(MainActivity.this, CrawlingActivity.class);
//                            intent.putExtra("htmlPageUrl", htmlPageUrl);
//                            startActivity(intent);

                            fakeWb.loadUrl(htmlPageUrl);

                            webViewAsyncTask = new WebviewAsyncTask();
                            webViewAsyncTask.execute();

                        }catch(IndexOutOfBoundsException | IllegalArgumentException e){
                            Toast.makeText(MainActivity.this, "????????? ?????? ???????????????.", Toast.LENGTH_SHORT).show();
                            Log.e(TAG, "OCR - onSuccess ???????????? : "+e );
                        }

                        //????????? ??????????????? ?????? ???????????? ???????????????

                        Log.w(TAG, "OCR -  onSuccess: "+firebaseVisionText.getTextBlocks().get(0).getText() );

                        //????????? acquired ???????????????
                        //?????? ???????????? 5????????????????????? ResourceExhaustedException ???
                        imageCapture.close();

                        //?????? ??????????????? ????????????
                        waitingDialog.dismiss();


                    }
                }).addOnFailureListener(new OnFailureListener() {
            @Override
            public void onFailure(@NonNull Exception e) {
                Log.w(TAG, "onFailure: ?????????????????? ????????? ?????? ??????");
                //?????? ??????????????? ????????????
                waitingDialog.dismiss();
            }


        });


    }

    //==============================================================================================




    //AR============================================================================================
    private void onUpdate(FrameTime frameTime) {
        //????????? ar ????????????????????? ???????????? ????????????..
        Frame frame = arFragment.getArSceneView().getArFrame();


 /*       if (isImageDetected)
            return;




        Collection<AugmentedImage> augmentedImages =
                frame.getUpdatedTrackables(AugmentedImage.class);


        for (AugmentedImage image : augmentedImages) {

            if (image.getTrackingState() == TrackingState.TRACKING) {

                if (image.getName().equals("airImage") || image.getName().equals("papers")
                ) {

                    isImageDetected = true;

                    playVideo(image.createAnchor(image.getCenterPose()), image.getExtentX(),
                            image.getExtentZ());

                    break;
                }

            }

        }*/

    }

    private void playVideo(Anchor anchor, float extentX, float extentZ) {

        mediaPlayer.start();

        AnchorNode anchorNode = new AnchorNode(anchor);

        texture.getSurfaceTexture().setOnFrameAvailableListener(surfaceTexture -> {
            anchorNode.setRenderable(renderable);
            texture.getSurfaceTexture().setOnFrameAvailableListener(null);
        });

        anchorNode.setWorldScale(new Vector3(extentX, 1f, extentZ));

        scene.addChild(anchorNode);

    }

    //==============================================================================================


    @Override
    protected void onStart() {
        super.onStart();

        //OpenCV====================================================================================
        /*boolean havePermission = true;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (checkSelfPermission(CAMERA) != PackageManager.PERMISSION_GRANTED) {
                requestPermissions(new String[]{CAMERA}, CAMERA_PERMISSION_REQUEST_CODE);
                havePermission = false;
            }
        }
        if (havePermission) {
            onCameraPermissionGranted();
        }*/
        //===========================================================================================
    }


    @Override
    public void onPause() {
        super.onPause();
        //OpenCV====================================================================================
        /*if (mOpenCvCameraView != null)
            mOpenCvCameraView.disableView();*/
        //===========================================================================================

        super.onPause();
    }

    @Override
    public void onResume() {
        super.onResume();
        htmlPageUrl ="https://search.naver.com/search.naver?where=video&sm=tab_jum&query=";

        //OpenCV====================================================================================
        /*if (!OpenCVLoader.initDebug()) {
            Log.d(TAG, "onResume :: Internal OpenCV library not found.");
            OpenCVLoader.initAsync(OpenCVLoader.OPENCV_VERSION_3_2_0, this, mLoaderCallback);
        } else {
            Log.d(TAG, "onResum :: OpenCV library found inside package. Using it!");
            mLoaderCallback.onManagerConnected(LoaderCallbackInterface.SUCCESS);
        }*/
        //===========================================================================================

    }


    public void onDestroy() {
        super.onDestroy();

        //OpenCV====================================================================================
        if (mOpenCvCameraView != null)
            mOpenCvCameraView.disableView();
        //===========================================================================================

    }


    //OpenCV ?????? ?????????============================================================================
    /*private BaseLoaderCallback mLoaderCallback = new BaseLoaderCallback(this) {
        @Override
        public void onManagerConnected(int status) {
            switch (status) {
                case LoaderCallbackInterface.SUCCESS:
                {
                    mOpenCvCameraView.enableView();
                } break;
                default:
                {
                    super.onManagerConnected(status);
                } break;
            }
        }
    };


    @Override
    public void onCameraViewStarted(int width, int height) {

    }

    @Override
    public void onCameraViewStopped() {

    }

    @Override
    public Mat onCameraFrame(CameraBridgeViewBase.CvCameraViewFrame inputFrame) {

        matInput = inputFrame.rgba();
        if ( matResult == null )
            matResult = new Mat(matInput.rows(), matInput.cols(), matInput.type());
        FindMemberNameInPaper(matInput.getNativeObjAddr(), matResult.getNativeObjAddr());
        return matResult;
    }


    protected List<? extends CameraBridgeViewBase> getCameraViewList() {
        return Collections.singletonList(mOpenCvCameraView);
    }


    //??????????????? ????????? ?????? ?????????
    private static final int CAMERA_PERMISSION_REQUEST_CODE = 200;


    protected void onCameraPermissionGranted() {
        List<? extends CameraBridgeViewBase> cameraViews = getCameraViewList();
        if (cameraViews == null) {
            return;
        }
        for (CameraBridgeViewBase cameraBridgeViewBase: cameraViews) {
            if (cameraBridgeViewBase != null) {
                cameraBridgeViewBase.setCameraPermissionGranted();
            }
        }
    }



    @Override
    @TargetApi(Build.VERSION_CODES.M)
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        if (requestCode == CAMERA_PERMISSION_REQUEST_CODE && grantResults.length > 0
                && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            onCameraPermissionGranted();
        }else{
            showDialogForPermission("?????? ??????????????? ???????????? ????????????????????????.");
        }
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
    }


    @TargetApi(Build.VERSION_CODES.M)
    private void showDialogForPermission(String msg) {


        AlertDialog.Builder builder = new AlertDialog.Builder( MainActivity.this);
        builder.setTitle("??????");
        builder.setMessage(msg);
        builder.setCancelable(false);
        builder.setPositiveButton("???", new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int id){
                requestPermissions(new String[]{CAMERA}, CAMERA_PERMISSION_REQUEST_CODE);
            }
        });
        builder.setNegativeButton("?????????", new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface arg0, int arg1) {
                finish();
            }
        });
        builder.create().show();
    }*/
    //==============================================================================================


    //Image to Bitmap===============================================================================
    //(ar fragment ?????? ????????? ????????? ?????????????????? ?????? ???????????????)
    private static byte[] imageToByte(Image image){
        byte[] byteArray = null;
        byteArray = NV21toJPEG(YUV420toNV21(image),image.getWidth(),image.getHeight(),100);
        return byteArray;
    }

    private static byte[] NV21toJPEG(byte[] nv21, int width, int height, int quality) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        YuvImage yuv = new YuvImage(nv21, ImageFormat.NV21, width, height, null);
        yuv.compressToJpeg(new Rect(0, 0, width, height), quality, out);
        return out.toByteArray();
    }

    private static byte[] YUV420toNV21(Image image) {
        byte[] nv21;
        // Get the three planes.
        ByteBuffer yBuffer = image.getPlanes()[0].getBuffer();
        ByteBuffer uBuffer = image.getPlanes()[1].getBuffer();
        ByteBuffer vBuffer = image.getPlanes()[2].getBuffer();

        int ySize = yBuffer.remaining();
        int uSize = uBuffer.remaining();
        int vSize = vBuffer.remaining();


        nv21 = new byte[ySize + uSize + vSize];

        //U and V are swapped
        yBuffer.get(nv21, 0, ySize);
        vBuffer.get(nv21, ySize, vSize);
        uBuffer.get(nv21, ySize + vSize, uSize);

        return nv21;
    }

    //crawling ?????? class==============================================================================================
    public class MyJavascriptInterface {
        @JavascriptInterface
        public void getHtml(String html){
            //??? ????????????????????? ???????????? ????????? html??? ????????????.
            source = html;
        }
    }

    private class WebviewAsyncTask extends AsyncTask<Void, Void, Void> {
        @Override
        protected void onPreExecute() {
            super.onPreExecute();
        }


        @Override
        protected Void doInBackground(Void... voids) {
            try {
                Thread.sleep(2000);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }

            return null;
        }

        @Override
        protected void onPostExecute(Void aVoid) {
            super.onPostExecute(aVoid);
            jsoupAsyncTask = new JsoupAsyncTask();
            jsoupAsyncTask.execute();
        }
    }



    private class JsoupAsyncTask extends AsyncTask<Void, Void, Void> {

        @Override
        protected void onPreExecute() {
            super.onPreExecute();

        }

        @Override
        protected Void doInBackground(Void... params) {

            //Document doc = Jsoup.connect(htmlPageUrl).get();
            try {
                //?????? webview?????? ????????? String source??? Document??? ????????????.
                Document doc = Jsoup.parse(source);
                Log.e("crawling", "source: "+ source);

                Elements urlElements = doc.select("li[id=item_index1]");
                //linkUrl??? ????????????.
                String linkUrl = urlElements.attr("data-cr-url");
                Log.e("crawling", "linkUrl: "+linkUrl);


                String novaLinkUrl = linkUrl.split("/")[3];
                Log.e("crawling", "novaLinkUrl : "+novaLinkUrl);

                if(novaLinkUrl.equals("teamnovaopen")){
                    //video??? source??? ????????????.
                    Elements videoSrcElements = doc.select("li[id=item_index1] div div a");
                    videoSrc = videoSrcElements.attr("data-api");
                    Log.e("crawling", "videoSrc: "+ videoSrc);

                    Document forVideo = Jsoup.connect(videoSrc).get();

                    Elements videoUrlElements = forVideo.select("body");
                    String videoUrl = videoUrlElements.text();
                    Log.e("crawling", "videoUrl: "+videoUrl);

                    String splitVideoUrl = videoUrl.split("sPlayUrl\":\"")[1];
                    playUrl = splitVideoUrl.split("\"")[0];

                    Log.e("crawling", "playUrl: "+ playUrl);
                }else{

                    playUrl="https://media.fmkorea.com/files/attach/new/20191101/3655109/2089104173/2339677357/1cb879a979e99e75f598d2e9038bfa4e.gif.mp4?d";
                }


            } catch (IOException e) {
                e.printStackTrace();
            }


            return null;
        }

        @Override
        protected void onPostExecute(Void result) {
            Toast.makeText(MainActivity.this, "????????? ???????????? ??????", Toast.LENGTH_SHORT).show();
            htmlPageUrl ="https://search.naver.com/search.naver?where=video&sm=tab_jum&query=";

            setAR(playUrl);
        }
    }

    public void setAR(String url){
        texture = new ExternalTexture();
        mediaPlayer = new MediaPlayer();
//        mediaPlayer = MediaPlayer.create(this, Uri.parse("https://serviceapi.nmv.naver.com/view/ugcPlayer.nhn?vid=7DCA747C80C640305145C42E13B6329C4660&inKey=V1268b72f809c30d1ef02c87c44d03bf0878269db9d3b7e681252ba8028ec7566512ec87c44d03bf08782&wmode=opaque&hasLink=1&autoPlay=false&beginTime=0"));
//
//        mediaPlayer = MediaPlayer.create(this, R.raw.test_video);
        try {
//            mediaPlayer.setDataSource("https://serviceapi.nmv.naver.com/view/ugcPlayer.nhn?vid=7DCA747C80C640305145C42E13B6329C4660&inKey=V1268b72f809c30d1ef02c87c44d03bf0878269db9d3b7e681252ba8028ec7566512ec87c44d03bf08782&wmode=opaque&hasLink=1&autoPlay=false&beginTime=0");

            if(url.equals("https://media.fmkorea.com/files/attach/new/20191101/3655109/2089104173/2339677357/1cb879a979e99e75f598d2e9038bfa4e.gif.mp4?d")){
                Toast.makeText(MainActivity.this, "????????? ?????? ????????? ???????????????", Toast.LENGTH_SHORT).show();
            }
            mediaPlayer.setDataSource(url);
            mediaPlayer.prepare();
        } catch (IOException e) {
            e.printStackTrace();
            Log.w(TAG, "mediaPlayer.setDataSource: fail" );
        }

        mediaPlayer.setSurface(texture.getSurface());
        mediaPlayer.setLooping(true);

        ModelRenderable
                .builder()
                .setSource(this, Uri.parse("video_screen.sfb"))
                .build()
                .thenAccept(modelRenderable -> {
                    modelRenderable.getMaterial().setExternalTexture("videoTexture",
                            texture);
                    modelRenderable.getMaterial().setFloat4("keyColor",
                            new Color(0.01843f, 1f, 0.098f));

                    renderable = modelRenderable;
                });

        arFragment = (CustomArFragment)
                getSupportFragmentManager().findFragmentById(R.id.ar_fragment);

        scene = arFragment.getArSceneView().getScene();

        scene.addOnUpdateListener(this::onUpdate);
    }


}

