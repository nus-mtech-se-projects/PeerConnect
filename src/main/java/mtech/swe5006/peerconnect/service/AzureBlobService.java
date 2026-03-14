package mtech.swe5006.peerconnect.service;

import com.azure.storage.blob.BlobClient;
import com.azure.storage.blob.BlobContainerClient;
import com.azure.storage.blob.BlobServiceClient;
import com.azure.storage.blob.BlobServiceClientBuilder;
import com.azure.storage.blob.models.BlobHttpHeaders;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;

@Service
public class AzureBlobService {

    private final BlobContainerClient containerClient;
    private final boolean enabled;

    public AzureBlobService(
            @Value("${azure.storage.enabled:false}") boolean enabled,
            @Value("${azure.storage.connection_string}") String connectionString,
            @Value("${azure.storage.container_name:avatars}") String containerName) {

        this.enabled = enabled;
        if (!enabled) {
            this.containerClient = null;
            return;
        }

        BlobServiceClient serviceClient = new BlobServiceClientBuilder()
                .connectionString(connectionString)
                .buildClient();

        this.containerClient = serviceClient.getBlobContainerClient(containerName);

        // Create the container if it doesn't exist
        if (!containerClient.exists()) {
            containerClient.create();
        }
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
