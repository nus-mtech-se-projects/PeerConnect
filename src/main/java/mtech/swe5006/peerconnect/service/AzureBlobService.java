package mtech.swe5006.peerconnect.service;

import com.azure.storage.blob.BlobClient;
import com.azure.storage.blob.BlobContainerClient;
import com.azure.storage.blob.BlobServiceClient;
import com.azure.storage.blob.BlobServiceClientBuilder;
import com.azure.storage.blob.models.BlobHttpHeaders;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;

@Service
public class AzureBlobService {

    private static final Logger log = LoggerFactory.getLogger(AzureBlobService.class);

    private final BlobContainerClient containerClient;
    private final boolean enabled;

        public AzureBlobService(
            @Value("${azure.storage.enabled:false}") boolean enabled,
            @Value("${azure.storage.connection_string}") String connectionString,
            @Value("${azure.storage.container_name:avatars}") String containerName) {

        BlobContainerClient resolvedContainerClient = null;
        boolean resolvedEnabled = false;
        boolean hasConnectionString = connectionString != null && !connectionString.isBlank();
        if (!hasConnectionString) {
            if (enabled) {
                log.warn("Azure storage is enabled but no connection string is configured. Disabling blob integration.");
            }
        } else {
            try {
                BlobServiceClient serviceClient = new BlobServiceClientBuilder()
                        .connectionString(connectionString)
                        .buildClient();

                resolvedContainerClient = serviceClient.getBlobContainerClient(containerName);

                if (!resolvedContainerClient.exists()) {
                    resolvedContainerClient.create();
                }
                resolvedEnabled = true;
            } catch (IllegalArgumentException ex) {
                log.warn("Azure storage connection string is invalid. Disabling blob integration.", ex);
            }
        }

        this.containerClient = resolvedContainerClient;
        this.enabled = resolvedEnabled;
    }

    /**
     * Upload a file to Azure Blob Storage, overwriting any existing blob with the same name.
     *
     * @param blobName the name to store the blob as (e.g. "userId.jpg")
     * @param file     the uploaded file
     * @return the public URL of the uploaded blob
     */
    public String upload(String blobName, MultipartFile file) throws IOException {
        if (!enabled || containerClient == null) {
            throw new IllegalStateException("Azure storage is disabled");
        }
        BlobClient blobClient = containerClient.getBlobClient(blobName);

        // Set content type so the blob is served correctly in browsers
        BlobHttpHeaders headers = new BlobHttpHeaders()
                .setContentType(file.getContentType());

        blobClient.upload(file.getInputStream(), file.getSize(), true); // true = overwrite
        blobClient.setHttpHeaders(headers);

        return blobClient.getBlobUrl();
    }

    /**
     * Delete a blob if it exists.
     */
    public void delete(String blobName) {
        if (!enabled || containerClient == null) {
            return;
        }
        BlobClient blobClient = containerClient.getBlobClient(blobName);
        blobClient.deleteIfExists();
    }
}
