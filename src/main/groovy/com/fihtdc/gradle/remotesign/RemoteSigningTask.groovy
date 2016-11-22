package com.fihtdc.gradle.remotesign

import com.android.annotations.NonNull
import com.android.build.gradle.api.ApplicationVariant
import com.android.build.gradle.internal.scope.PackagingScope
import com.android.build.gradle.internal.scope.TaskConfigAction
import com.android.build.gradle.internal.tasks.IncrementalTask
import com.mysql.jdbc.Driver
import okhttp3.Credentials
import okhttp3.MediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.Response
import org.gradle.api.Task
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.OutputFile

import java.sql.Connection
import java.sql.ResultSet
import java.sql.SQLException
import java.sql.Statement
import java.util.concurrent.TimeUnit

/**
 * Created by Jason on 11/17/2016.
 */
class RemoteSigningTask extends IncrementalTask {
    private static final String REQUEST_URL = """http://%s:%d/Jenkins/job/Remote_APK_CERT_TOOL/\
buildWithParameters?assertMethod=online&KEY_SET=%s&APK_CERT=%s&REQ_ID=%s&APK_NAME=%s"""

    private static final String DOWNLOAD_URL = """http://%s:%d/Jenkins/job/Remote_APK_CERT_TOOL/\
%d/artifact/output/%s"""

    private static final String DB_URL = "jdbc:mysql://%s/Jenkins"

    private def mConfig
    private File mInputFile
    private File mOutputFile

    private OkHttpClient mClient

    @Override
    protected void doFullTaskAction() throws IOException {
        createHttpClient()
        if (mInputFile.name.endsWith(".apk")) {
            try {
                signApk(mInputFile, mOutputFile)
            } catch (IOException | SQLException e) {
                logger.error(e.getMessage(), e)
            }
        }
    }

    void setRemoteSigningConfig(remoteSigningConfig) {
        mConfig = remoteSigningConfig
    }

    void setInputFile(File inputFile) {
        mInputFile = inputFile
        mOutputFile = inputFile
    }

    @InputFile
    File getInputFile() {
        return mInputFile
    }

    void setOutputFile(File outputFile) {
        mOutputFile = outputFile
    }

    @OutputFile
    File getOutputFile() {
        return mOutputFile
    }

    @Override
    public Task configure(Closure closure) {
        RemoteSigningTask task = (RemoteSigningTask) super.configure(closure)
        /* String inName = task.inputFile.name
        String outName = inName.replaceAll("\\.apk", "") + "-signed.apk"
        File outputFile = new File(task.inputFile.parent, outName)
        task.outputFile = task.inputFile*/
        return task
    }

    private void createHttpClient() {
        OkHttpClient.Builder builder = new OkHttpClient.Builder()
                .connectTimeout(5, TimeUnit.SECONDS)

        try {
            String proxyHost = System.getProperty("http.proxyHost", null)
            int proxyPort = Integer.parseInt(System.getProperty("http.proxyPort", null))
            String proxyUser = System.getProperty("http.proxyUser", null)
            String proxyPassword = System.getProperty("http.proxyPassword", null)

            if (proxyHost != null) {
                logger.info("Found proxy '{}' in gradle settings.", proxyHost)
                Proxy proxy = new Proxy(Proxy.Type.HTTP,
                        new InetSocketAddress(proxyHost, proxyPort))
                builder.proxy(proxy)

                if (proxyUser != null && proxyPassword != null) {
                    builder.proxyAuthenticator({ route, response ->
                        String credential = Credentials.basic(proxyUser, proxyPassword)
                        logger.info("Use proxy credential {}", credential)
                        return response.request().newBuilder()
                                .header("Proxy-Authorization", credential)
                                .build()
                    })
                }
            }
        } catch (NumberFormatException e) {
            // ignored
        }

        mClient = builder.build()
    }

    private void signApk(@NonNull File inputFile, @NonNull File outputFile)
            throws IOException, SQLException {
        logger.info("Signing APK with config {}", mConfig)

        long reqId = new Date().time

        String url = String.format(Locale.getDefault(), REQUEST_URL, mConfig.getServerHost(),
                mConfig.getServerPort(), mConfig.getKeySet(), mConfig.getApkCert(), reqId,
                inputFile.getName())
        logger.debug("url={}", url)

        MultipartBody multiPart = new MultipartBody.Builder()
                .setType(MultipartBody.FORM)
                .addFormDataPart("temp/source.apk", inputFile.getName(), RequestBody.create(
                MediaType.parse("application/vnd.android.package-archive"), inputFile))
                .build()

        ProgressRequestBody.Listener listener = { progress ->
            // logger.info("{}%", progress)
            System.out.print(".")
            System.out.flush()
        }
        ProgressRequestBody requestBody = new ProgressRequestBody(multiPart, listener)

        String credential = Credentials.basic(mConfig.getUsername(), mConfig.getPassword())
        Request request = new Request.Builder()
                .url(url)
                .method("POST", requestBody)
                .addHeader("Authorization", credential)
                .build()
        logger.info("Uploading APK...")

        // System.out.println("Uploading APK...")
        Response response = mClient.newCall(request).execute()
        System.out.println("")
        logger.debug("response={}", response)
        if (response.code() == 201) {
            logger.info("Waiting the signing task to be done...")
            // System.out.print("Waiting the signing task to be done...")
            int jobId = getJobId(reqId, "Remote_APK_CERT_TOOL")
            System.out.println()
            logger.debug("jobId={}", jobId)
            downloadApk(jobId, inputFile.getName(), outputFile, listener)
        }
    }

    private void downloadApk(int jobNo, String fileName, File outputFile,
                             ProgressRequestBody.Listener listener) throws IOException {
        String url = String.format(Locale.getDefault(), DOWNLOAD_URL, mConfig.getServerHost(),
                mConfig.getServerPort(), jobNo, fileName)
        logger.info("Downloading apk from {}...", url)

        Request request = new Request.Builder().url(url)
                .addHeader("Authorization",
                Credentials.basic(mConfig.getUsername(), mConfig.getPassword()))
                .addHeader("Content-Type", "application/vnd.android.package-archive")
                .build()

        Response response = mClient.newCall(request).execute()
        logger.debug("response={}", response)

        long contentLength = response.body().contentLength()
        InputStream ins = response.body().byteStream()

        // System.out.println("Downloading apk from " + url + "...")
        byte[] buffer = new byte[4096]
        File tempFile = new File(mOutputFile.parent, "temp.apk");

        if (tempFile.exists() && !tempFile.delete()) {
            throw new IOException("Failed to delete temp APK")
        }
        FileOutputStream out = new FileOutputStream(tempFile)
        int byteCounts = 0
        for (int len; (len = ins.read(buffer)) != -1;) {
            out.write(buffer, 0, len)
            if (contentLength != -1) {
                byteCounts += len
                listener.onProgress((int) (100F * byteCounts / contentLength))
            }
        }
        out.flush()
        out.close()
        response.body().close()
        System.out.println()

        if (mOutputFile.exists() && !mOutputFile.delete()) {
            throw new IOException("Failed to rename APK")
        }
        if (!tempFile.renameTo(mOutputFile)) {
            throw new IOException("Failed to rename APK")
        }
    }

    private int getJobId(long reqId, String jobName) throws SQLException {
        String url = String.format(Locale.getDefault(), DB_URL, mConfig.getServerHost())
        logger.debug("dbUrl={}", url)

        Driver driver = new Driver()
        String sql = "SELECT job_id FROM job_status WHERE job_name='%s' AND request_id='%d'"
        sql = String.format(Locale.getDefault(), sql, jobName, reqId)
        logger.debug("sql={}", sql)


        Properties info = new Properties();
        info.put("user", mConfig.getDbUser());
        info.put("password", mConfig.getDbPassword());
        Connection conn = driver.connect(url, info)
        /*Connection conn = DriverManager.getConnection(url, mConfig.getDbUser(),
                mConfig.getDbPassword())*/
        Statement stmt = conn.createStatement()
        ResultSet rs = stmt.executeQuery(sql)
        int jobId = rs.next() ? rs.getInt(1) : -1
        // STEP 6: Clean-up environment
        rs.close()
        stmt.close()
        conn.close()

        if (jobId == -1) {
            try {
                Thread.sleep(2000)
            } catch (InterruptedException e) {
                e.printStackTrace()
            }
            System.out.print(".")
            System.out.flush()
            return getJobId(reqId, jobName)
        }
        return jobId
    }

    public static class ConfigAction implements TaskConfigAction<RemoteSigningTask> {
        private PackagingScope mPackagingScope
        private ApplicationVariant mApplicationVariant

        public ConfigAction(PackagingScope packagingScope, ApplicationVariant variant) {
            mPackagingScope = packagingScope
            mApplicationVariant = variant
        }

        @Override
        public String getName() {
            return mPackagingScope.getTaskName("remoteSign")
        }

        @Override
        public Class<RemoteSigningTask> getType() {
            return RemoteSigningTask.class
        }

        @Override
        public void execute(@NonNull RemoteSigningTask task) {
            task.configure {
                androidBuilder = mPackagingScope.androidBuilder
                variantName = mPackagingScope.fullVariantName
                remoteSigningConfig = mApplicationVariant.buildType.remoteSigningConfig
                inputFile = new File("${mPackagingScope.outputApk}".replaceAll("-unsigned", ""))
            }
        }
    }
}
