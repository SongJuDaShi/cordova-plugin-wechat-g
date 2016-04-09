package org.apache.cordova.wechat;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.StringReader;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Random;

import org.apache.cordova.CallbackContext;
import org.apache.cordova.CordovaArgs;
import org.apache.cordova.CordovaPlugin;
import org.apache.cordova.PluginResult;
import org.apache.http.NameValuePair;
import org.apache.http.message.BasicNameValuePair;
import org.json.JSONException;
import org.json.JSONObject;
import org.xmlpull.v1.XmlPullParser;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.AsyncTask;
import android.os.Environment;
import android.util.Base64;
import android.util.Log;
import android.util.Xml;
import android.webkit.URLUtil;

import com.tencent.mm.sdk.modelmsg.SendAuth;
import com.tencent.mm.sdk.modelmsg.SendMessageToWX;
import com.tencent.mm.sdk.modelmsg.WXAppExtendObject;
import com.tencent.mm.sdk.modelmsg.WXEmojiObject;
import com.tencent.mm.sdk.modelmsg.WXFileObject;
import com.tencent.mm.sdk.modelmsg.WXImageObject;
import com.tencent.mm.sdk.modelmsg.WXMediaMessage;
import com.tencent.mm.sdk.modelmsg.WXMusicObject;
import com.tencent.mm.sdk.modelmsg.WXTextObject;
import com.tencent.mm.sdk.modelmsg.WXVideoObject;
import com.tencent.mm.sdk.modelmsg.WXWebpageObject;
import com.tencent.mm.sdk.modelpay.PayReq;
import com.tencent.mm.sdk.openapi.IWXAPI;
import com.tencent.mm.sdk.openapi.WXAPIFactory;

public class Wechat extends CordovaPlugin {

	public static final String TAG = "Cordova.Plugin.Wechat";

	public static final String WEIXIN_APP_ID = "wx1611845a4dc9b397";
	public static final String WEIXIN_MCH_ID = "1262631501";
	public static final String WEIXIN_API_KEY = "gouxinpaysecret20150612gouxinpay";

	public static final String ERROR_WECHAT_NOT_INSTALLED = "未安装微信";
	public static final String ERROR_INVALID_PARAMETERS = "参数格式错误";
	public static final String ERROR_SEND_REQUEST_FAILED = "发送请求失败";
	public static final String ERROR_WECHAT_RESPONSE_COMMON = "普通错误";
	public static final String ERROR_WECHAT_RESPONSE_USER_CANCEL = "用户点击取消并返回";
	public static final String ERROR_WECHAT_RESPONSE_SENT_FAILED = "发送失败";
	public static final String ERROR_WECHAT_RESPONSE_AUTH_DENIED = "授权失败";
	public static final String ERROR_WECHAT_RESPONSE_UNSUPPORT = "微信不支持";
	public static final String ERROR_WECHAT_RESPONSE_UNKNOWN = "未知错误";

	public static final String EXTERNAL_STORAGE_IMAGE_PREFIX = "external://";

	public static final String KEY_ARG_MESSAGE = "message";
	public static final String KEY_ARG_SCENE = "scene";
	public static final String KEY_ARG_TEXT = "text";
	public static final String KEY_ARG_MESSAGE_TITLE = "title";
	public static final String KEY_ARG_MESSAGE_DESCRIPTION = "description";
	public static final String KEY_ARG_MESSAGE_THUMB = "thumb";
	public static final String KEY_ARG_MESSAGE_MEDIA = "media";
	public static final String KEY_ARG_MESSAGE_MEDIA_TYPE = "type";
	public static final String KEY_ARG_MESSAGE_MEDIA_WEBPAGEURL = "webpageUrl";
	public static final String KEY_ARG_MESSAGE_MEDIA_IMAGE = "image";
	public static final String KEY_ARG_MESSAGE_MEDIA_TEXT = "text";
	public static final String KEY_ARG_MESSAGE_MEDIA_MUSICURL = "musicUrl";
	public static final String KEY_ARG_MESSAGE_MEDIA_MUSICDATAURL = "musicDataUrl";
	public static final String KEY_ARG_MESSAGE_MEDIA_VIDEOURL = "videoUrl";
	public static final String KEY_ARG_MESSAGE_MEDIA_FILE = "file";
	public static final String KEY_ARG_MESSAGE_MEDIA_EMOTION = "emotion";
	public static final String KEY_ARG_MESSAGE_MEDIA_EXTINFO = "extInfo";
	public static final String KEY_ARG_MESSAGE_MEDIA_URL = "url";

	public static final int TYPE_WECHAT_SHARING_APP = 1;
	public static final int TYPE_WECHAT_SHARING_EMOTION = 2;
	public static final int TYPE_WECHAT_SHARING_FILE = 3;
	public static final int TYPE_WECHAT_SHARING_IMAGE = 4;
	public static final int TYPE_WECHAT_SHARING_MUSIC = 5;
	public static final int TYPE_WECHAT_SHARING_VIDEO = 6;
	public static final int TYPE_WECHAT_SHARING_WEBPAGE = 7;
	public static final int TYPE_WECHAT_SHARING_TEXT = 8;

	public static final int SCENE_SESSION = 0;
	public static final int SCENE_TIMELINE = 1;
	public static final int SCENE_FAVORITE = 2;

	public static final int MAX_THUMBNAIL_SIZE = 320;

	public static Wechat instance = null;

	protected CallbackContext currentCallbackContext;
	protected IWXAPI wxAPI;
	protected String appId;
	protected PayReq req;
	protected StringBuffer sb;
	private Map<String, String> resultunifiedorder;

	@Override
	protected void pluginInitialize() {

		super.pluginInitialize();

		instance = this;

		initWXAPI();

		Log.d(TAG, "plugin initialized.");
	}

	protected void initWXAPI() {
		if (wxAPI == null) {
			String appId = getAppId();

			wxAPI = WXAPIFactory.createWXAPI(webView.getContext(), appId, true);
			wxAPI.registerApp(appId);
		}
	}

	public IWXAPI getWxAPI() {
		return wxAPI;
	}

	public CallbackContext getCurrentCallbackContext() {
		return currentCallbackContext;
	}

	@Override
	public boolean execute(String action, CordovaArgs args, CallbackContext callbackContext) throws JSONException {
		Log.d(TAG, String.format("%s is called. Callback ID: %s.", action, callbackContext.getCallbackId()));

		if (action.equals("share")) {
			return share(args, callbackContext);
		} else if (action.equals("sendAuthRequest")) {
			return sendAuthRequest(args, callbackContext);
		} else if (action.equals("sendPaymentRequest")) {
			// return sendPaymentRequest(args, callbackContext);
			return weixinPay(args, callbackContext);
		} else if (action.equals("isWXAppInstalled")) {
			return isInstalled(callbackContext);
		}

		return false;
	}

	protected boolean share(CordovaArgs args, final CallbackContext callbackContext) throws JSONException {
		final IWXAPI api = getWXAPI();

		// check if installed
		if (!api.isWXAppInstalled()) {
			callbackContext.error(ERROR_WECHAT_NOT_INSTALLED);
			return true;
		}

		// check if # of arguments is correct
		final JSONObject params;
		try {
			params = args.getJSONObject(0);
		} catch (JSONException e) {
			callbackContext.error(ERROR_INVALID_PARAMETERS);
			return true;
		}

		final SendMessageToWX.Req req = new SendMessageToWX.Req();
		req.transaction = buildTransaction();

		if (params.has(KEY_ARG_SCENE)) {
			switch (params.getInt(KEY_ARG_SCENE)) {
			case SCENE_FAVORITE:
				req.scene = SendMessageToWX.Req.WXSceneFavorite;
				break;
			case SCENE_TIMELINE:
				req.scene = SendMessageToWX.Req.WXSceneTimeline;
				break;
			case SCENE_SESSION:
				req.scene = SendMessageToWX.Req.WXSceneSession;
				break;
			default:
				req.scene = SendMessageToWX.Req.WXSceneTimeline;
			}
		} else {
			req.scene = SendMessageToWX.Req.WXSceneTimeline;
		}

		// run in background
		cordova.getThreadPool().execute(new Runnable() {

			@Override
			public void run() {
				try {
					req.message = buildSharingMessage(params);
				} catch (JSONException e) {
					Log.e(TAG, "Failed to build sharing message.", e);

					// clear callback context
					currentCallbackContext = null;

					// send json exception error
					callbackContext.sendPluginResult(new PluginResult(PluginResult.Status.JSON_EXCEPTION));
				}

				if (api.sendReq(req)) {
					Log.i(TAG, "Message has been sent successfully.");
				} else {
					Log.i(TAG, "Message has been sent unsuccessfully.");

					// clear callback context
					currentCallbackContext = null;

					// send error
					callbackContext.error(ERROR_SEND_REQUEST_FAILED);
				}
			}
		});

		// send no result
		sendNoResultPluginResult(callbackContext);

		return true;
	}

	protected boolean sendAuthRequest(CordovaArgs args, CallbackContext callbackContext) {
		final IWXAPI api = getWXAPI();

		final SendAuth.Req req = new SendAuth.Req();
		try {
			req.scope = args.getString(0);
			req.state = args.getString(1);
		} catch (JSONException e) {
			Log.e(TAG, e.getMessage());

			req.scope = "snsapi_userinfo";
			req.state = "wechat";
		}

		if (api.sendReq(req)) {
			Log.i(TAG, "Auth request has been sent successfully.");

			// send no result
			sendNoResultPluginResult(callbackContext);
		} else {
			Log.i(TAG, "Auth request has been sent unsuccessfully.");

			// send error
			callbackContext.error(ERROR_SEND_REQUEST_FAILED);
		}

		return true;
	}

	protected boolean sendPaymentRequest(CordovaArgs args, CallbackContext callbackContext) {

		final IWXAPI api = getWXAPI();

		// check if # of arguments is correct
		final JSONObject params;
		try {
			params = args.getJSONObject(0);
		} catch (JSONException e) {
			callbackContext.error(ERROR_INVALID_PARAMETERS);
			return true;
		}

		PayReq req = new PayReq();

		try {
			req.appId = getAppId();
			req.partnerId = params.has("mch_id") ? params.getString("mch_id") : params.getString("partnerid");
			req.prepayId = params.has("prepay_id") ? params.getString("prepay_id") : params.getString("prepayid");
			req.nonceStr = params.has("nonce") ? params.getString("nonce") : params.getString("noncestr");
			req.timeStamp = params.getString("timestamp");
			req.sign = params.getString("sign");
			req.packageValue = "Sign=WXPay";
		} catch (Exception e) {
			Log.e(TAG, e.getMessage());

			callbackContext.error(ERROR_INVALID_PARAMETERS);
			return true;
		}

		if (api.sendReq(req)) {
			Log.i(TAG, "Payment request has been sent successfully.");

			// send no result
			sendNoResultPluginResult(callbackContext);
		} else {
			Log.i(TAG, "Payment request has been sent unsuccessfully.");

			// send error
			callbackContext.error(ERROR_SEND_REQUEST_FAILED);
		}

		return true;
	}

	protected boolean isInstalled(CallbackContext callbackContext) {
		final IWXAPI api = getWXAPI();

		if (!api.isWXAppInstalled()) {
			callbackContext.success(0);
		} else {
			callbackContext.success(1);
		}

		return true;
	}

	protected WXMediaMessage buildSharingMessage(JSONObject params) throws JSONException {
		Log.d(TAG, "Start building message.");

		// media parameters
		WXMediaMessage.IMediaObject mediaObject = null;
		WXMediaMessage wxMediaMessage = new WXMediaMessage();

		if (params.has(KEY_ARG_TEXT)) {
			WXTextObject textObject = new WXTextObject();
			textObject.text = params.getString(KEY_ARG_TEXT);
			mediaObject = textObject;
			wxMediaMessage.description = textObject.text;
		} else {
			JSONObject message = params.getJSONObject(KEY_ARG_MESSAGE);
			JSONObject media = message.getJSONObject(KEY_ARG_MESSAGE_MEDIA);

			wxMediaMessage.title = message.getString(KEY_ARG_MESSAGE_TITLE);
			wxMediaMessage.description = message.getString(KEY_ARG_MESSAGE_DESCRIPTION);

			// thumbnail
			Bitmap thumbnail = getThumbnail(message, KEY_ARG_MESSAGE_THUMB);
			if (thumbnail != null) {
				wxMediaMessage.setThumbImage(thumbnail);
				thumbnail.recycle();
			}

			// check types
			int type = media.has(KEY_ARG_MESSAGE_MEDIA_TYPE) ? media.getInt(KEY_ARG_MESSAGE_MEDIA_TYPE)
					: TYPE_WECHAT_SHARING_WEBPAGE;

			switch (type) {
			case TYPE_WECHAT_SHARING_APP:
				WXAppExtendObject appObject = new WXAppExtendObject();
				appObject.extInfo = media.getString(KEY_ARG_MESSAGE_MEDIA_EXTINFO);
				appObject.filePath = media.getString(KEY_ARG_MESSAGE_MEDIA_URL);
				mediaObject = appObject;
				break;

			case TYPE_WECHAT_SHARING_EMOTION:
				WXEmojiObject emoObject = new WXEmojiObject();
				InputStream emoji = getFileInputStream(media.getString(KEY_ARG_MESSAGE_MEDIA_EMOTION));
				if (emoji != null) {
					try {
						emoObject.emojiData = Util.readBytes(emoji);
					} catch (IOException e) {
						e.printStackTrace();
					}
				}
				mediaObject = emoObject;
				break;

			case TYPE_WECHAT_SHARING_FILE:
				WXFileObject fileObject = new WXFileObject();
				fileObject.filePath = media.getString(KEY_ARG_MESSAGE_MEDIA_FILE);
				mediaObject = fileObject;
				break;

			case TYPE_WECHAT_SHARING_IMAGE:
				Bitmap image = getBitmap(message.getJSONObject(KEY_ARG_MESSAGE_MEDIA), KEY_ARG_MESSAGE_MEDIA_IMAGE, 0);
				mediaObject = new WXImageObject(image);
				image.recycle();
				break;

			case TYPE_WECHAT_SHARING_MUSIC:
				WXMusicObject musicObject = new WXMusicObject();
				musicObject.musicUrl = media.getString(KEY_ARG_MESSAGE_MEDIA_MUSICURL);
				musicObject.musicDataUrl = media.getString(KEY_ARG_MESSAGE_MEDIA_MUSICDATAURL);
				mediaObject = musicObject;
				break;

			case TYPE_WECHAT_SHARING_VIDEO:
				WXVideoObject videoObject = new WXVideoObject();
				videoObject.videoUrl = media.getString(KEY_ARG_MESSAGE_MEDIA_VIDEOURL);
				mediaObject = videoObject;
				break;

			case TYPE_WECHAT_SHARING_WEBPAGE:
			default:
				mediaObject = new WXWebpageObject(media.getString(KEY_ARG_MESSAGE_MEDIA_WEBPAGEURL));
			}
		}

		wxMediaMessage.mediaObject = mediaObject;

		return wxMediaMessage;
	}

	protected IWXAPI getWXAPI() {
		return wxAPI;
	}

	private String buildTransaction() {
		return String.valueOf(System.currentTimeMillis());
	}

	private String buildTransaction(final String type) {
		return type + System.currentTimeMillis();
	}

	protected Bitmap getThumbnail(JSONObject message, String key) {
		return getBitmap(message, key, MAX_THUMBNAIL_SIZE);
	}

	protected Bitmap getBitmap(JSONObject message, String key, int maxSize) {
		Bitmap bmp = null;
		String url = null;

		try {
			if (!message.has(key)) {
				return null;
			}

			url = message.getString(key);

			// get input stream
			InputStream inputStream = getFileInputStream(url);
			if (inputStream == null) {
				return null;
			}

			// decode it
			// @TODO make sure the image is not too big, or it will cause out of
			// memory
			BitmapFactory.Options options = new BitmapFactory.Options();
			bmp = BitmapFactory.decodeStream(inputStream, null, options);

			// scale
			if (maxSize > 0 && (options.outWidth > maxSize || options.outHeight > maxSize)) {

				Log.d(TAG, String.format("Bitmap was decoded, dimension: %d x %d, max allowed size: %d.",
						options.outWidth, options.outHeight, maxSize));

				int width = 0;
				int height = 0;

				if (options.outWidth > options.outHeight) {
					width = maxSize;
					height = width * options.outHeight / options.outWidth;
				} else {
					height = maxSize;
					width = height * options.outWidth / options.outHeight;
				}

				Bitmap scaled = Bitmap.createScaledBitmap(bmp, width, height, true);
				bmp.recycle();

				bmp = scaled;
			}

			inputStream.close();

		} catch (JSONException e) {
			bmp = null;
			e.printStackTrace();
		} catch (IOException e) {
			bmp = null;
			e.printStackTrace();
		}

		return bmp;
	}

	/**
	 * Get input stream from a url
	 * 
	 * @param url
	 * @return
	 */
	protected InputStream getFileInputStream(String url) {
		try {

			InputStream inputStream = null;

			if (URLUtil.isHttpUrl(url) || URLUtil.isHttpsUrl(url)) {

				File file = Util.downloadAndCacheFile(webView.getContext(), url);

				if (file == null) {
					Log.d(TAG, String.format("File could not be downloaded from %s.", url));
					return null;
				}

				url = file.getAbsolutePath();
				inputStream = new FileInputStream(file);

				Log.d(TAG, String.format("File was downloaded and cached to %s.", url));

			} else if (url.startsWith("data:image")) { // base64 image

				String imageDataBytes = url.substring(url.indexOf(",") + 1);
				byte imageBytes[] = Base64.decode(imageDataBytes.getBytes(), Base64.DEFAULT);
				inputStream = new ByteArrayInputStream(imageBytes);

				Log.d(TAG, "Image is in base64 format.");

			} else if (url.startsWith(EXTERNAL_STORAGE_IMAGE_PREFIX)) { // external
																		// path

				url = Environment.getExternalStorageDirectory().getAbsolutePath()
						+ url.substring(EXTERNAL_STORAGE_IMAGE_PREFIX.length());
				inputStream = new FileInputStream(url);

				Log.d(TAG, String.format("File is located on external storage at %s.", url));

			} else if (!url.startsWith("/")) { // relative path

				inputStream = cordova.getActivity().getApplicationContext().getAssets().open(url);

				Log.d(TAG, String.format("File is located in assets folder at %s.", url));

			} else {

				inputStream = new FileInputStream(url);

				Log.d(TAG, String.format("File is located at %s.", url));

			}

			return inputStream;

		} catch (FileNotFoundException e) {
			e.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
		}

		return null;
	}

	protected String getAppId() {
		if (this.appId == null) {
			this.appId = preferences.getString(WEIXIN_APP_ID, "");
		}

		return this.appId;
	}

	private void sendNoResultPluginResult(CallbackContext callbackContext) {
		// save current callback context
		currentCallbackContext = callbackContext;

		// send no result and keep callback
		PluginResult result = new PluginResult(PluginResult.Status.NO_RESULT);
		result.setKeepCallback(true);
		callbackContext.sendPluginResult(result);
	}

	/*
	 * 调用微信支付
	 */
	public boolean weixinPay(CordovaArgs args, final CallbackContext callbackContext) throws JSONException {
		Log.d(TAG, "WeixinPay start.");
		currentCallbackContext = callbackContext;
		initpay(args.getJSONObject(0), callbackContext);
		Log.d(TAG, "WeixinPay end.");
		return true;
	}

	/*
	 * 初始化微信支付
	 */
	private void initpay(JSONObject json, final CallbackContext callbackContext) {
		Log.d(TAG, "initpay start.");
		req = new PayReq();
		sb = new StringBuffer();
		GetPrepayIdTask getPrepayId = new GetPrepayIdTask();
		getPrepayId.execute(json);
		Log.d(TAG, "initpay end.");
	}

	private void genPayReq(final CallbackContext callbackContext) {

		Log.d(TAG, "genPayReq start.");

		req.appId = WEIXIN_APP_ID;
		req.partnerId = WEIXIN_MCH_ID;
		req.prepayId = resultunifiedorder.get("prepay_id");
		req.packageValue = "Sign=WXPay";
		req.nonceStr = genNonceStr();
		req.timeStamp = String.valueOf(genTimeStamp());

		List<NameValuePair> signParams = new LinkedList<NameValuePair>();
		signParams.add(new BasicNameValuePair("appid", req.appId));
		signParams.add(new BasicNameValuePair("noncestr", req.nonceStr));
		signParams.add(new BasicNameValuePair("package", req.packageValue));
		signParams.add(new BasicNameValuePair("partnerid", req.partnerId));
		signParams.add(new BasicNameValuePair("prepayid", req.prepayId));
		signParams.add(new BasicNameValuePair("timestamp", req.timeStamp));

		req.sign = genAppSign(signParams, callbackContext);

		sb.append("sign\n" + req.sign + "\n\n");

		wxAPI.registerApp(WEIXIN_APP_ID);
		wxAPI.sendReq(req);

		Log.d(TAG, "genPayReq end.");

	}

	private String genAppSign(List<NameValuePair> params, final CallbackContext callbackContext) {
		Log.d(TAG, "genAppSign start.");
		StringBuilder sb = new StringBuilder();

		for (int i = 0; i < params.size(); i++) {
			sb.append(params.get(i).getName());
			sb.append('=');
			sb.append(params.get(i).getValue());
			sb.append('&');
		}
		sb.append("key=");
		sb.append(WEIXIN_API_KEY);

		this.sb.append("sign str\n" + sb.toString() + "\n\n");
		String appSign = MD5.getMessageDigest(sb.toString().getBytes()).toUpperCase();
		Log.d(TAG, appSign);
		Log.d(TAG, "genAppSign end.");
		return appSign;
	}

	private class GetPrepayIdTask extends AsyncTask<JSONObject, Void, Map<String, String>> {

		@Override
		protected void onPreExecute() {
		}

		@Override
		protected void onPostExecute(Map<String, String> result) {
			Log.d(TAG, "GetPrepayIdTask.onPostExecute start.");
			sb.append("prepay_id\n" + result.get("prepay_id") + "\n\n");
			resultunifiedorder = result;
			genPayReq(currentCallbackContext);
			Log.d(TAG, "GetPrepayIdTask.onPostExecute end.");
		}

		@Override
		protected void onCancelled() {
			super.onCancelled();
		}

		@Override
		protected Map<String, String> doInBackground(JSONObject... params) {

			Log.d(TAG, "GetPrepayIdTask.doInBackground start.");
			String url = String.format("https://api.mch.weixin.qq.com/pay/unifiedorder");
			String entity = genProductArgs(params[0], currentCallbackContext);
			byte[] buf = Util.httpPost(url, entity);
			String content = new String(buf);
			Map<String, String> xml = decodeXml(content);
			Log.d(TAG, "GetPrepayIdTask.doInBackground end.");

			return xml;
		}
	}

	private String genProductArgs(JSONObject json, final CallbackContext callbackContext) {
		Log.d(TAG, "genProductArgs start.");
		StringBuffer xml = new StringBuffer();

		try {
			String nonceStr = genNonceStr();

			xml.append("</xml>");
			List<NameValuePair> packageParams = new LinkedList<NameValuePair>();
			packageParams.add(new BasicNameValuePair("appid", WEIXIN_APP_ID));
			packageParams.add(new BasicNameValuePair("body", json.getString("productName")));
			packageParams.add(new BasicNameValuePair("mch_id", WEIXIN_MCH_ID));
			packageParams.add(new BasicNameValuePair("nonce_str", nonceStr));
			packageParams.add(new BasicNameValuePair("notify_url", json.getString("notify_url")));
			packageParams.add(new BasicNameValuePair("out_trade_no", json.getString("outTradeNo")));
			packageParams.add(new BasicNameValuePair("spbill_create_ip", "127.0.0.1"));
			packageParams.add(new BasicNameValuePair("total_fee", json.getString("total_fee")));
			packageParams.add(new BasicNameValuePair("trade_type", "APP"));

			String sign = genPackageSign(packageParams, callbackContext);
			packageParams.add(new BasicNameValuePair("sign", sign));

			String xmlstring = toXml(packageParams);

			Log.d(TAG, "genProductArgs end.");
			return xmlstring;

		} catch (Exception e) {
			Log.e(TAG, "genProductArgs error", e.fillInStackTrace());
			return null;
		}

	}

	private String genNonceStr() {
		Random random = new Random();
		return MD5.getMessageDigest(String.valueOf(random.nextInt(10000)).getBytes());
	}

	private long genTimeStamp() {
		return System.currentTimeMillis() / 1000;
	}

	private String genOutTradNo() {
		Random random = new Random();
		return MD5.getMessageDigest(String.valueOf(random.nextInt(10000)).getBytes());
	}

	private String genPackageSign(List<NameValuePair> params, final CallbackContext callbackContext) {
		StringBuilder sb = new StringBuilder();

		for (int i = 0; i < params.size(); i++) {
			sb.append(params.get(i).getName());
			sb.append('=');
			sb.append(params.get(i).getValue());
			sb.append('&');
		}
		sb.append("key=");
		sb.append(WEIXIN_APP_ID);

		String packageSign = MD5.getMessageDigest(sb.toString().getBytes()).toUpperCase();
		return packageSign;
	}

	private String toXml(List<NameValuePair> params) {
		Log.d(TAG, "toXml Start.");
		StringBuilder sb = new StringBuilder();
		sb.append("<xml>");
		for (int i = 0; i < params.size(); i++) {
			sb.append("<" + params.get(i).getName() + ">");

			sb.append(params.get(i).getValue());
			sb.append("</" + params.get(i).getName() + ">");
		}
		sb.append("</xml>");

		Log.d(TAG, sb.toString());
		Log.d(TAG, "toXml end.");
		return sb.toString();
	}

	public Map<String, String> decodeXml(String content) {

		try {
			Map<String, String> xml = new HashMap<String, String>();
			XmlPullParser parser = Xml.newPullParser();
			parser.setInput(new StringReader(content));
			int event = parser.getEventType();
			while (event != XmlPullParser.END_DOCUMENT) {

				String nodeName = parser.getName();
				switch (event) {
				case XmlPullParser.START_DOCUMENT:

					break;
				case XmlPullParser.START_TAG:

					if ("xml".equals(nodeName) == false) {
						xml.put(nodeName, parser.nextText());
					}
					break;
				case XmlPullParser.END_TAG:
					break;
				}
				event = parser.next();
			}

			return xml;
		} catch (Exception e) {
			Log.e(TAG, e.toString());
		}
		return null;

	}
}
