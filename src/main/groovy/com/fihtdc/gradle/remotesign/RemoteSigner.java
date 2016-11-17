
package com.fihtdc.gradle.remotesign;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.InetSocketAddress;
import java.net.Proxy;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Date;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

import okhttp3.Credentials;
import okhttp3.MediaType;
import okhttp3.MultipartBody;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

/**
 * Created by Jason on 11/12/2016.
 */
public class RemoteSigner {
    private static final Logger LOGGER = LoggerFactory.getLogger(RemoteSigner.class);

    private static final String REQUEST_URL = "http://%s:%d/Jenkins/job/Remote_APK_CERT_TOOL/buildWithParameters"
            + "?assertMethod=online&KEY_SET=%s&APK_CERT=%s&REQ_ID=%s&APK_NAME=%s";

    private static final String DOWNLOAD_URL = "http://%s:%d/Jenkins/job/Remote_APK_CERT_TOOL/%d"
            + "/artifact/output/%s";

    private static final String JDBC_DRIVER = "com.mysql.jdbc.Driver";

    private static final String DB_URL = "jdbc:mysql://%s/Jenkins";

    private OkHttpClient mClient;

    private RemoteSigningConfig mConfigs;

    private Logger mLogger = LOGGER;

    public RemoteSigner(Logger logger, RemoteSigningConfig configs) {
        mLogger = logger;
        mConfigs = configs;

        try {
            Class.forName(JDBC_DRIVER);
        } catch (ClassNotFoundException e) {
            mLogger.error(e.getMessage(), e);
        }

        OkHttpClient.Builder builder = new OkHttpClient.Builder()
                .connectTimeout(5, TimeUnit.SECONDS);

        try {
            String proxyHost = System.getProperty("http.proxyHost", null);
            int proxyPort = Integer.parseInt(System.getProperty("http.proxyPort", null));
            String proxyUser = System.getProperty("http.proxyUser", null);
            String proxyPassword = System.getProperty("http.proxyPassword", null);

            if (proxyHost != null) {
                mLogger.info("Found proxy '{}' in gradle settings.", proxyHost);
                Proxy proxy = new Proxy(Proxy.Type.HTTP,
                        new InetSocketAddress(proxyHost, proxyPort));
                builder.proxy(proxy);

                if (proxyUser != null && proxyPassword != null) {
                    builder.proxyAuthenticator((route, response) -> {
                        String credential = Credentials.basic(proxyUser, proxyPassword);
                        mLogger.info("Use proxy credential {}", credential);
                        return response.request().newBuilder()
                                .header("Proxy-Authorization", credential)
                                .build();
                    });
                }
            }
        } catch (NumberFormatException e) {
            // ignored
        }

        mClient = builder.build();
    }

    public void signApk(File apkFile) {
        mLogger.info("Signing APK with config {}", mConfigs);

        long reqId = new Date().getTime();

        String url = String.format(Locale.getDefault(), REQUEST_URL, mConfigs.getServerHost(),
                mConfigs.getServerPort(), mConfigs.getKeySet(), mConfigs.getApkCert(), reqId,
                apkFile.getName());
        mLogger.debug("url={}", url);

        RequestBody requestBody = new MultipartBody.Builder()
                .setType(MultipartBody.FORM)
                .addFormDataPart("temp/source.apk", apkFile.getName(), RequestBody.create(
                        MediaType.parse("application/vnd.android.package-archive"), apkFile))
                .build();

        Request request = new Request.Builder()
                .url(url)
                .method("POST", requestBody)
                .addHeader("Authorization",
                        Credentials.basic(mConfigs.getUsername(), mConfigs.getPassword()))
                .build();

        try {
            Response response = mClient.newCall(request).execute();
            mLogger.debug("response={}", response);
            if (response.code() == 201) {
                int jobId = getJobId(reqId, "Remote_APK_CERT_TOOL");
                LOGGER.debug("jobId={}", jobId);
                downloadApk(jobId, apkFile);
            }
        } catch (IOException | SQLException e) {
            mLogger.error(e.getMessage(), e);
        }
    }

    private void downloadApk(int jobNo, File apkFile) {
        String url = String.format(Locale.getDefault(), DOWNLOAD_URL, mConfigs.getServerHost(),
                mConfigs.getServerPort(), jobNo, apkFile.getName());
        mLogger.info("Downloading apk from {}...", url);

        Request request = new Request.Builder().url(url)
                .addHeader("Authorization",
                        Credentials.basic(mConfigs.getUsername(), mConfigs.getPassword()))
                .addHeader("Content-Type", "application/vnd.android.package-archive")
                .build();

        try {
            Response response = mClient.newCall(request).execute();
            mLogger.debug("response={}", response);
            response.headers();

            InputStream ins = response.body().byteStream();

            byte[] buffer = new byte[2048];

            File outFile = new File(apkFile.getParent(), "sign-temp.apk");
            FileOutputStream out = new FileOutputStream(outFile);

            for (int len; (len = ins.read(buffer)) != -1;) {
                out.write(buffer, 0, len);
            }
            out.flush();
            out.close();
            response.body().close();

            apkFile.delete();
            outFile.renameTo(apkFile);
        } catch (IOException e) {
            mLogger.error(e.getMessage(), e);
        }
    }

    private int getJobId(long reqId, String jobName) throws SQLException {
        String url = String.format(Locale.getDefault(), DB_URL, mConfigs.getServerHost());
        mLogger.debug("dbUrl={}", url);

        String sql = "select job_id from job_status where job_name='%s' and request_id='%d'";
        sql = String.format(Locale.getDefault(), sql, jobName, reqId);
        mLogger.debug("sql={}", sql);
        Connection conn = DriverManager.getConnection(url, mConfigs.getDbUser(),
                mConfigs.getDbPassword());
        Statement stmt = conn.createStatement();
        ResultSet rs = stmt.executeQuery(sql);
        int jobId = rs.next() ? rs.getInt(1) : -1;
        // STEP 6: Clean-up environment
        rs.close();
        stmt.close();
        conn.close();

        if (jobId == -1) {
            try {
                Thread.sleep(2000);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            return getJobId(reqId, jobName);
        }
        return jobId;
    }
}
