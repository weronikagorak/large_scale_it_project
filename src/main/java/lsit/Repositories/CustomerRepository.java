package lsit.Repositories;

import java.net.URI;
import java.util.*;

import org.springframework.stereotype.Repository;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import io.github.cdimascio.dotenv.Dotenv;
import lsit.Models.Customer;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.AwsCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.ListObjectsRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.S3Object;
/**
 * Repository for managing Customer data.
 */
@Repository
public class CustomerRepository implements ICustomerRepository {
    
    final String BUCKET="pizzeria_bucket";
    final String PREFIX="pizzeria/customers/";
    final String ENDPOINT_URL="https://storage.googleapis.com";

    S3Client s3client;
    AwsCredentials awsCredentials;
    
    public CustomerRepository(){
        Dotenv dotenv = Dotenv.load();

        String accessKey = dotenv.get("ACCESS_KEY");
        String secretKey = dotenv.get("SECRET_KEY");

        if (accessKey == null || secretKey == null) {
            throw new IllegalStateException("AWS credentials are not set in environment variables");
        }
        awsCredentials = AwsBasicCredentials.create(accessKey, secretKey);
        s3client = S3Client.builder()
            .credentialsProvider(StaticCredentialsProvider.create(awsCredentials))
            .endpointOverride(URI.create(ENDPOINT_URL))
            .region(Region.of("auto"))
            .build();
    }

    /**
     * Adds a new customer.
     * Generates a unique UUID for the customer.
     * 
     * @param c The customer to add.
     */
    public void add(Customer c) {
        try{
            // TO DO make random only if not specified
            c.setId(UUID.randomUUID());

            ObjectMapper om = new ObjectMapper();
            String customerJson = om.writeValueAsString(c);
            
            s3client.putObject(PutObjectRequest.builder()
                .bucket(BUCKET)
                .key(PREFIX + c.getId().toString())
                .build(),
                RequestBody.fromString(customerJson)
            );
        }
        catch(JsonProcessingException e){
            // Log error
            e.printStackTrace();
        }
    }

    /**
     * Retrieves a customer by ID.
     * 
     * @param id The UUID of the customer.
     * @return The Customer object or null if not found.
     */
    public Customer get(UUID id) {
        try{
            var objectBytes = s3client.getObject(GetObjectRequest.builder()
                .bucket(BUCKET)
                .key(PREFIX + id.toString())
                .build()
            ).readAllBytes();

            ObjectMapper om = new ObjectMapper();
            Customer c = om.readValue(objectBytes, Customer.class);

            return c;
        }catch(Exception e){
            return null;
        }
    }

    /**
     * Removes a customer by ID.
     * 
     * @param id The UUID of the customer to remove.
     */
    public void remove(UUID id){
        s3client.deleteObject(DeleteObjectRequest.builder()
            .bucket(BUCKET)
            .key(PREFIX + id.toString())
            .build()
        );  
    }

    /**
     * Updates an existing customer's details.
     * 
     * @param c The customer with updated information.
     */
    public void update(Customer c){
        try{
            Customer existing = this.get(c.getId());
            if(existing == null) return;

            ObjectMapper om = new ObjectMapper();
            String customerJson = om.writeValueAsString(c);
            s3client.putObject(PutObjectRequest.builder()
                .bucket(BUCKET)
                .key(PREFIX + c.getId().toString())
                .build(),
                RequestBody.fromString(customerJson)
            );
        }
        catch(JsonProcessingException e){
            // Log error
            e.printStackTrace();
        }
    }

    /**
     * Lists all customers.
     * 
     * @return A list of all customers.
     */
    public List<Customer> list(){
        List<Customer> customers = new ArrayList<>();
        List<S3Object> objects = s3client.listObjects(ListObjectsRequest.builder()
          .bucket(BUCKET)
          .prefix(PREFIX)
          .build()  
        ).contents();

        for(S3Object o : objects){
            try {
                String key = o.key();
                if (key.length() > PREFIX.length()) {
                    String idString = key.substring(PREFIX.length());
                    UUID id = UUID.fromString(idString);
                    Customer c = this.get(id);
                    if (c != null) {
                        customers.add(c);
                    }
                }
            } catch (Exception e) {
                System.err.println("Error processing customer object: " + o.key());
                e.printStackTrace();
            }
        }

        return customers;
    }
}
