/*
 * Copyright contributors to the Galasa project
 *
 * SPDX-License-Identifier: EPL-2.0
 */
package dev.galasa.ras.couchdb.internal;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import javax.validation.constraints.NotNull;
import javax.xml.catalog.CatalogFeatures.Feature;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.http.HttpEntity;
import org.apache.http.HttpStatus;
import org.apache.http.StatusLine;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpEntityEnclosingRequestBase;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.methods.HttpPut;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.util.EntityUtils;

import com.google.gson.Gson;

import dev.galasa.framework.spi.ConfigurationPropertyStoreException;
import dev.galasa.framework.spi.IConfigurationPropertyStoreService;
import dev.galasa.framework.spi.IFramework;
import dev.galasa.framework.spi.IResultArchiveStoreDirectoryService;
import dev.galasa.framework.spi.IResultArchiveStoreService;
import dev.galasa.framework.spi.IRun;
import dev.galasa.framework.spi.ResultArchiveStoreException;
import dev.galasa.framework.spi.ras.ResultArchiveStoreFileStore;
import dev.galasa.framework.spi.teststructure.TestStructure;
import dev.galasa.framework.spi.utils.GalasaGsonBuilder;
import dev.galasa.ras.couchdb.internal.dependencies.api.HttpClientFactory;
import dev.galasa.ras.couchdb.internal.dependencies.impl.HttpClientFactoryImpl;
import dev.galasa.ras.couchdb.internal.dependencies.impl.LogFactoryImpl;
import dev.galasa.ras.couchdb.internal.pojos.Artifacts;
import dev.galasa.ras.couchdb.internal.pojos.LogLines;
import dev.galasa.ras.couchdb.internal.pojos.PutPostResponse;

public class CouchdbRasStore implements IResultArchiveStoreService {

    private final Log                          logger            ;

    private final IFramework                   framework;                                         // NOSONAR
    private final URI                          rasUri;

    private final CloseableHttpClient          httpClient;
    private boolean                            shutdown           = false;

    private final Gson                         gson               = GalasaGsonBuilder.build();

    private final CouchdbRasFileSystemProvider provider;

    private final IRun                         run;
    private String                             runDocumentId;
    private String                             runDocumentRevision;

    private long                               logOrder           = 0;

    private final ArrayList<String>            logCache           = new ArrayList<>(100);

    private ArrayList<String>                  logIds             = new ArrayList<>();
    private ArrayList<String>                  artifactDocumentId = new ArrayList<>();;
    private String                             artifactDocumentRev;

    private TestStructure                      lastTestStructure;

    private final boolean featureFlagOneArtifactPerDocument ;

    private dev.galasa.ras.couchdb.internal.dependencies.api.LogFactory logFactory; 

    // Configuration property store so we can look up feature flags.
    private final IConfigurationPropertyStoreService cps;

    // The namespace used to access cps properties that this store is interested in.
    public static final String CPS_NAMESPACE_COUCHDB = "couchdb";

    private final int inlineArtifactMaxSize  ;

    public CouchdbRasStore(IFramework framework, URI rasUri) throws CouchdbRasException {
        this(framework, rasUri, new HttpClientFactoryImpl() , new CouchdbValidatorImpl() , new LogFactoryImpl() );
    }

    // Note: We use logFactory here so we can propogate it downwards during unit testing.
    public CouchdbRasStore(IFramework framework, URI rasUri, HttpClientFactory httpFactory , CouchdbValidator validator, 
        dev.galasa.ras.couchdb.internal.dependencies.api.LogFactory logFactory 
    ) throws CouchdbRasException {
        this.logFactory = logFactory;
        this.logger = logFactory.getLog(getClass());
        this.framework = framework;
        this.rasUri = rasUri;
         // *** Validate the connection to the server and it's version
        this.httpClient = httpFactory.createClient();

        validator.checkCouchdbDatabaseIsValid(rasUri,this.httpClient);

        // Set the cps up.
        try {
            this.cps = this.framework.getConfigurationPropertyService(CPS_NAMESPACE_COUCHDB);
        } catch (ConfigurationPropertyStoreException ex ) {
            throw new CouchdbRasException("Unable to connect to a configuration property store.",ex);
        }

        // Dig out the value of the feature flag once, and hold it in a cache variable.
        this.featureFlagOneArtifactPerDocument = isFeatureEnabled(CpsPropertyDef.ONE_ARTIFACT_PER_DOCUMENT);
        this.inlineArtifactMaxSize = CpsPropertyDef.INLINE_ARTIFACT_MAX_SIZE.getCpsIntValue(this.logger, this.cps);

        this.run = this.framework.getTestRun();

        // *** If this is a run, ensure we can create the run document
        if (this.run != null) {
            lastTestStructure = new TestStructure();
            lastTestStructure.setRunName(this.run.getName());
            try {
                updateTestStructure(lastTestStructure);
            } catch (ResultArchiveStoreException e) {
                throw new CouchdbRasException("Validation failed - unable to create initial run document", e);
            }

            if (!this.isFeatureFlagOneArtifactPerDocumentEnabled()){
                createArtifactDocument();
            }
        }

        ResultArchiveStoreFileStore fileStore = new ResultArchiveStoreFileStore();
        this.provider = new CouchdbRasFileSystemProvider(fileStore, this, this.logFactory);
    }


    // Protected so that we can create artifact documents from elsewhere.
    protected void createArtifactDocument() throws CouchdbRasException {
        Artifacts artifacts = new Artifacts();
        createArtifactDocument(artifacts);
    }

    protected void createArtifactDocument(Artifacts artifacts) throws CouchdbRasException {

        artifacts.runId = this.runDocumentId;
        artifacts.runName = this.run.getName();

        String jsonArtifacts = gson.toJson(artifacts);

        HttpPost request = new HttpPost(this.rasUri + "/galasa_artifacts");
        request.setEntity(new StringEntity(jsonArtifacts, StandardCharsets.UTF_8));
        request.addHeader("Accept", "application/json");
        request.addHeader("Content-Type", "application/json");

        try (CloseableHttpResponse response = httpClient.execute(request)) {
            StatusLine statusLine = response.getStatusLine();
            if (statusLine.getStatusCode() != HttpStatus.SC_CREATED) {
                throw new CouchdbRasException("Unable to store the artifacts document - " + statusLine.toString());
            }

            HttpEntity entity = response.getEntity();
            PutPostResponse putPostResponse = gson.fromJson(EntityUtils.toString(entity), PutPostResponse.class);
            if (putPostResponse.id == null || putPostResponse.rev == null) {
                throw new CouchdbRasException("Unable to store the artifacts document - Invalid JSON response");
            }

            this.artifactDocumentId.add(putPostResponse.id);
            this.artifactDocumentRev = putPostResponse.rev;
        } catch (CouchdbRasException e) {
            throw e;
        } catch (Exception e) {
            throw new CouchdbRasException("Unable to store the artifacts document", e);
        }
    }

    @Override
    public void writeLog(@NotNull String message) throws ResultArchiveStoreException {
        if (this.run == null) {
            throw new ResultArchiveStoreException("Not a run");
        }

        String[] lines = message.split("\r\n?|\n");

        synchronized (logCache) {
            logCache.addAll(Arrays.asList(lines));
            if (logCache.size() >= 100) {
                flushLogCache();
            }
        }
    }

    private void flushLogCache() throws ResultArchiveStoreException {
        LogLines logLines = new LogLines();
        synchronized (logCache) {
            if (logCache.isEmpty()) {
                return;
            }
            logLines.lines = new ArrayList<>(logCache);
            logOrder++;
            logLines.order = logOrder;

            logCache.clear();
        }
        logLines.runName = this.run.getName();
        logLines.runId = this.runDocumentId;

        String jsonStructure = gson.toJson(logLines);

        HttpPost request = new HttpPost(this.rasUri + "/galasa_log");
        request.setEntity(new StringEntity(jsonStructure, StandardCharsets.UTF_8));
        request.addHeader("Accept", "application/json");
        request.addHeader("Content-Type", "application/json");

        try (CloseableHttpResponse response = httpClient.execute(request)) {
            StatusLine statusLine = response.getStatusLine();
            if (statusLine.getStatusCode() != HttpStatus.SC_CREATED) {
                throw new CouchdbRasException("Unable to store the test log - " + statusLine.toString());
            }

            HttpEntity entity = response.getEntity();
            PutPostResponse putPostResponse = gson.fromJson(EntityUtils.toString(entity), PutPostResponse.class);
            if (putPostResponse.id == null || putPostResponse.rev == null) {
                throw new CouchdbRasException("Unable to store the test log - Invalid JSON response");
            }

            this.logIds.add(putPostResponse.id);

            this.updateTestStructure(lastTestStructure);
        } catch (CouchdbRasException e) {
            throw e;
        } catch (Exception e) {
            throw new ResultArchiveStoreException("Unable to store the test log", e);
        }
    }

    @Override
    public void writeLog(@NotNull List<String> messages) throws ResultArchiveStoreException {
        if (this.run == null) {
            throw new ResultArchiveStoreException("Not a run");
        }

        for (String message : messages) {
            writeLog(message);
        }
    }

    @Override
    public synchronized void updateTestStructure(@NotNull TestStructure testStructure)
            throws ResultArchiveStoreException {
        if (this.run == null) {
            throw new ResultArchiveStoreException("Not a run");
        }

        this.lastTestStructure = testStructure;
        this.lastTestStructure.setLogRecordIds(this.logIds);
        this.lastTestStructure.setArtifactRecordIds(this.artifactDocumentId);
        this.lastTestStructure.normalise();

        String jsonStructure = gson.toJson(testStructure);

        HttpEntityEnclosingRequestBase request;
        if (runDocumentId == null) {
            request = new HttpPost(this.rasUri + "/galasa_run");
        } else {
            request = new HttpPut(this.rasUri + "/galasa_run/" + runDocumentId);
            request.addHeader("If-Match", runDocumentRevision);
        }
        request.setEntity(new StringEntity(jsonStructure, StandardCharsets.UTF_8));
        request.addHeader("Accept", "application/json");
        request.addHeader("Content-Type", "application/json");

        try (CloseableHttpResponse response = httpClient.execute(request)) {
            StatusLine statusLine = response.getStatusLine();
            if (statusLine.getStatusCode() != HttpStatus.SC_CREATED) {
                if (statusLine.getStatusCode() == HttpStatus.SC_CONFLICT) {
                    logger.error(
                            "The run document has been updated by another engine, terminating now to avoid corruption");
                    System.exit(0);
                }

                throw new CouchdbRasException("Unable to store the test structure - " + statusLine.toString());
            }

            HttpEntity entity = response.getEntity();
            PutPostResponse putPostResponse = gson.fromJson(EntityUtils.toString(entity), PutPostResponse.class);
            if (putPostResponse.id == null || putPostResponse.rev == null) {
                throw new CouchdbRasException("Unable to store the test structure - Invalid JSON response");
            }
            this.runDocumentId = putPostResponse.id;
            this.runDocumentRevision = putPostResponse.rev;

        } catch (CouchdbRasException e) {
            throw e;
        } catch (Exception e) {
            throw new ResultArchiveStoreException("Unable to store the test structure", e);
        }
    }

    public void retrieveArtifact(CouchdbArtifactPath path, Path cachePath) throws CouchdbRasException {
        String artifactRecordId = path.getArtifactRecordId();
        String encodedPath;
        try {
            encodedPath = URLEncoder.encode(path.toString(), "utf-8");
        } catch (UnsupportedEncodingException e) {
            throw new CouchdbRasException("Problem encoding artifact path", e);
        }

        HttpGet httpGet = new HttpGet(this.rasUri + "/galasa_artifacts/" + artifactRecordId + "/" + encodedPath);

        try (CloseableHttpResponse response = httpClient.execute(httpGet)) {
            StatusLine statusLine = response.getStatusLine();
            if (statusLine.getStatusCode() == HttpStatus.SC_NOT_FOUND) {
                throw new CouchdbRasException("Not found - " + path.toString());
            }
            if (statusLine.getStatusCode() != HttpStatus.SC_OK) {
                throw new CouchdbRasException("Unable to find artifact - " + statusLine.toString());
            }

            HttpEntity entity = response.getEntity();
            Files.copy(entity.getContent(), cachePath, StandardCopyOption.REPLACE_EXISTING);
        } catch (CouchdbRasException e) {
            throw e;
        } catch (Exception e) {
            throw new CouchdbRasException("Unable to find runs", e);
        }
    }

    public String getLog(TestStructure ts) throws CouchdbRasException {
        StringBuilder sb = new StringBuilder();

        for (String logRecordId : ts.getLogRecordIds()) {
            HttpGet httpGet = new HttpGet(this.rasUri + "/galasa_log/" + logRecordId);
            httpGet.addHeader("Accept", "application/json");

            try (CloseableHttpResponse response = httpClient.execute(httpGet)) {
                StatusLine statusLine = response.getStatusLine();
                if (statusLine.getStatusCode() == HttpStatus.SC_NOT_FOUND) { // TODO Ignore it for now
                    continue;
                }
                if (statusLine.getStatusCode() != HttpStatus.SC_OK) {
                    throw new CouchdbRasException("Unable to find logs - " + statusLine.toString());
                }

                HttpEntity entity = response.getEntity();
                String responseEntity = EntityUtils.toString(entity);
                LogLines logLines = gson.fromJson(responseEntity, LogLines.class);
                if (logLines.lines != null) {
                    for (String line : logLines.lines) {
                        if (sb.length() > 0) {
                            sb.append("\n");
                        }
                        sb.append(line);
                    }
                }
            } catch (CouchdbRasException e) {
                throw e;
            } catch (Exception e) {
                throw new CouchdbRasException("Unable to find runs", e);
            }
        }
        return sb.toString();
    }

    @Override
    public Path getStoredArtifactsRoot() {
        if (this.run == null) {
            return null;
        }
        return provider.getActualFileSystem().getPath("/");
    }

    @Override
    public void flush() {
        try {
            flushLogCache();
        } catch (ResultArchiveStoreException e) {
            logger.error("Error with heartbeat flush", e);
        }
    }

    @Override
    public void shutdown() {
        this.shutdown = true;
        try {
            flushLogCache();
        } catch (ResultArchiveStoreException e) {
            logger.error("Error with shutdown flush", e);
        }

        try {
            this.httpClient.close();
        } catch (IOException e) {
        }
    }

    protected boolean isShutdown() {
        return this.shutdown;
    }

    public CloseableHttpClient getHttpClient() {
        return this.httpClient;
    }

    public String getArtifactDocumentId() {
        return this.artifactDocumentId.get(0);
    }

    public String getArtifactDocumentRev() {
        return this.artifactDocumentRev;
    }

    public URI getCouchdbUri() {
        return this.rasUri;
    }

    public Gson getGson() {
        return this.gson;
    }

    public void updateArtifactDocumentRev(String newArtifactDocumentRev) {
        this.artifactDocumentRev = newArtifactDocumentRev;
    }

    @Override
    public @NotNull List<IResultArchiveStoreDirectoryService> getDirectoryServices() {
        ArrayList<IResultArchiveStoreDirectoryService> dirs = new ArrayList<>();
        dirs.add(new CouchdbDirectoryService(this, this.logFactory));
        return dirs;
    }

    @Override
    public String calculateRasRunId() {
        
        if (this.runDocumentId == null) {
            return null;
        }
        return "cdb-" + this.runDocumentId;
    }

    private boolean isFeatureEnabled(CpsPropertyDef flag) throws CouchdbRasException {
        String featurePropertyName = flag.getPropertyName();
        int firstDotIndex = featurePropertyName.indexOf('.');
        String prefix = featurePropertyName.substring(0, firstDotIndex);
        String suffix = featurePropertyName.substring(firstDotIndex+1);
        String value ;
        try {
            value = this.cps.getProperty(prefix, suffix);
        } catch( ConfigurationPropertyStoreException ex) {
            throw new CouchdbRasException(
                MessageFormat.format("Failed to get the value of property {0} from the cps.",featurePropertyName),
                ex
            );
        }

        boolean isFeatureEnabled = Boolean.parseBoolean(value);
        if (isFeatureEnabled) {
            logger.trace(MessageFormat.format("Feature flag {0} is enabled", featurePropertyName));
        } else {
            logger.trace(MessageFormat.format("Feature flag {0} is disabled", featurePropertyName));
        }
        return isFeatureEnabled;
    }



    public boolean isFeatureFlagOneArtifactPerDocumentEnabled() {
        return this.featureFlagOneArtifactPerDocument;
    }

    public int getInlineArtifactMaxSize() {
        return this.inlineArtifactMaxSize;
    }
}
