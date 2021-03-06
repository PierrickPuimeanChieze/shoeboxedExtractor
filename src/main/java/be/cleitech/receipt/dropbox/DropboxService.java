package be.cleitech.receipt.dropbox;

import be.cleitech.receipt.Utils;
import be.cleitech.receipt.tasks.PublishTask;
import com.dropbox.core.*;
import com.dropbox.core.json.JsonReader;
import com.dropbox.core.v2.DbxClientV2;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.*;

/**
 * Created by ppc on 1/30/2017.
 */
@Component
public class DropboxService {
    private static Log LOG = LogFactory.getLog(DropboxService.class);

    private final static String[] DROPBOX_SECRET_PATHS = new String[]{
            "./dropbox_client_secret.json",
            "/etc/shoeboxed-toolsuite/dropbox_client_secret.json",
            System.getenv("APPDATA") + "/shoeboxed-toolsuite/dropbox_client_secret.json",
            "~/.shoeboxed-toolsuite/dropbox_client_secret.json"
    };
  //  @Value("${dropbox.uploadPath}")
    private String uploadPath;

    private String accessToken;

    private DbxAuthFinish authFinish;

    private DbxAppInfo appInfo;

//    @Value("${credentials.directory}/dropboxAcessToken")
    private File accessTokenFile;

    public DropboxService(String uploadPath, File accessTokenFile) {
        this.uploadPath = uploadPath;
        this.accessTokenFile = accessTokenFile;
    }

    public void initDropboxAccessToken() {
        if (!accessTokenFile.exists()) {
            accessToken = retrieveDropBoxAccessToken();
            try (FileWriter fileWriter = new FileWriter(accessTokenFile)) {
                fileWriter.write(accessToken);
            } catch (IOException e) {
                throw new DropboxInitException("Unable to write acces token to " + accessTokenFile);
            }

        } else {
            try (
                    FileReader fileReader = new FileReader(accessTokenFile);
                    BufferedReader bufferedReader = new BufferedReader(fileReader)
            ) {
                accessToken = bufferedReader.readLine();
            } catch (IOException e) {
                throw new DropboxInitException("Unable to read from " + accessTokenFile);
            }
        }
    }

    public void uploadFile(File fileToUpload, String fileName, PublishTask publishTask) throws DbxException, IOException {
        // Create Dropbox client
        DbxRequestConfig config = new DbxRequestConfig("shoeboxed-toolsuite");
        DbxClientV2 client = new DbxClientV2(config, accessToken);

        // Upload file to Dropbox
        try (InputStream in = new FileInputStream(fileToUpload)) {
            client.files().uploadBuilder(uploadPath + "/" + fileName)
                    .uploadAndFinish(in);
        }
    }


    private String retrieveDropBoxAccessToken() {
        File dropBoxInfoFile = Utils.findConfFile(DROPBOX_SECRET_PATHS);
        if (dropBoxInfoFile == null) {
            throw new DropboxInitException("Unable to found dropbox client secret file");
        }
        try {
            appInfo = DbxAppInfo.Reader.readFromFile(dropBoxInfoFile);
        } catch (JsonReader.FileLoadException e) {
            throw new DropboxInitException("unable to load secret file from" + dropBoxInfoFile);
        }

        DbxRequestConfig requestConfig = new DbxRequestConfig("shoeboxed-toolsuite");
        DbxWebAuth webAuth = new DbxWebAuth(requestConfig, appInfo);
        DbxWebAuth.Request webAuthRequest = DbxWebAuth.newRequestBuilder()
                .withNoRedirect()
                .build();

        String authorizeUrl = webAuth.authorize(webAuthRequest);
        System.out.println("Go to " + authorizeUrl);
        System.out.print("Copy past the code :");
        String code = null;
        try {
            code = new BufferedReader(new InputStreamReader(System.in)).readLine();
        } catch (IOException e) {
            throw new DropboxInitException("Unable to read code from console");
        }
        if (code == null) {
            //FIXME : put an exception
            throw new RuntimeException("code==null");
        }

        LOG.info("Authorization Code :" + code);
        code = code.trim();
        try {
            authFinish = webAuth.finishFromCode(code);
        } catch (DbxException ex) {
            throw new RuntimeException("Error in DbxWebAuth.authorize: " + ex.getMessage());
        }

        LOG.info("Authorization complete." + "\n- User ID: " + authFinish.getUserId() + "\n- Access Token: " + authFinish.getAccessToken());
        return authFinish.getAccessToken();
    }
}
