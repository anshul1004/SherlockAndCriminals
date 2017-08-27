/**
 * Copyright 2016 IBM Corp. All Rights Reserved.
 * 
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License. You may obtain a copy of the License at
 * 
 * http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */
package net.bluemix.tutorial.vision;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;
import java.util.logging.Logger;

import javax.imageio.ImageIO;
import javax.ws.rs.Consumes;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.Status;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import com.amazonaws.auth.AWSStaticCredentialsProvider;
import com.amazonaws.auth.BasicAWSCredentials;
import com.amazonaws.regions.Regions;
import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.AmazonS3ClientBuilder;
import com.amazonaws.services.s3.model.GetObjectRequest;
import com.amazonaws.services.s3.model.ObjectMetadata;
import com.amazonaws.services.s3.model.PutObjectRequest;
import com.amazonaws.services.s3.model.S3Object;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.ibm.watson.developer_cloud.alchemy.v1.AlchemyVision;
import com.ibm.watson.developer_cloud.alchemy.v1.model.ImageFaces;
import com.ibm.watson.developer_cloud.http.ServiceCall;
import com.ibm.watson.developer_cloud.visual_recognition.v3.*;
import com.ibm.watson.developer_cloud.visual_recognition.v3.model.ClassifierOptions;
import com.ibm.watson.developer_cloud.visual_recognition.v3.model.ClassifyImagesOptions;
import com.ibm.watson.developer_cloud.alchemy.v1.model.ImageFace;
import com.ibm.watson.developer_cloud.visual_recognition.v3.model.VisualClassification;
import com.ibm.watson.developer_cloud.visual_recognition.v3.model.VisualClassifier;
import com.sun.jersey.core.header.FormDataContentDisposition;
import com.sun.jersey.multipart.FormDataParam;

import net.bluemix.tutorial.vision.Person;
/*
 * Analyze the uploaded image using Alchemy & Visual Recognition APIs
 * Result : JSON
 */
@Path("/analysis")
public class Analysis {
	
	/*
	 * Training images directory path
	 */
	final static String trainDirPath = "/home/anshul/NewDesktop/Training/";
	
	/*
	 * Training images directory name
	 */
	final static String trainDirName = "train";
	
	/*
	 * Threshold on result images
	 * Threshold value of 0.0 means each extracted faces with match >=0.0 are captured.
	 */
	final static double threshold = 0.0;

	/*
	 * Logger to log processing activities
	 */
	private static Logger LOGGER = Logger.getLogger(Analysis.class.getName());

	/*
	 * Gson helps to respond JSON in response
	 */
	private final Gson gson = new GsonBuilder().setPrettyPrinting().create();
	
	/*
	 * Visual Recognition
	 */
	private VisualRecognition vision;

	HashMap<String, String> imageUrls = new HashMap<>();

	ArrayList<Result> outputResults = new ArrayList<>();

	@POST
	@Consumes(MediaType.MULTIPART_FORM_DATA)
	@Produces("text/json")
	@Path("/result")
	public Response image(@FormDataParam("file") InputStream uploadedInputStream,
			@FormDataParam("file") FormDataContentDisposition fileDetail) throws Exception {
		try {

			vision = new VisualRecognition(VisualRecognition.VERSION_DATE_2016_05_20);
			vision.setApiKey("18362521d759ee4c6c341c50b2a1b1f2ca084266");

			// write it to disk
			File tmpFile = File.createTempFile("vision-", ".jpg");

			LOGGER.info("Analyzing a binary file " + tmpFile);
			Files.copy(uploadedInputStream, tmpFile.toPath(), StandardCopyOption.REPLACE_EXISTING);

			BasicAWSCredentials awsCreds = new BasicAWSCredentials("AKIAJX7EPJ6XWSWFSVYA",
					"5JUT5zrACjh9NguWLc2toJI8ZDDWp1DMxcUYQ0MH");
			AmazonS3 s3Client = AmazonS3ClientBuilder.standard()
					.withCredentials(new AWSStaticCredentialsProvider(awsCreds)).withRegion(Regions.AP_SOUTH_1).build();

			AlchemyVision av = new AlchemyVision();
			av.setApiKey("dbb4caf41597dbccc942e97f93c1f5b5ae32f12a");

			ServiceCall<ImageFaces> serviceImages = av.recognizeFaces(tmpFile, false);
			ImageFaces faces = serviceImages.execute();

			int i = 0;
			LOGGER.info("Total Faces Extracted: " + faces.getImageFaces().size());

			for (ImageFace face : faces.getImageFaces()) {
				LOGGER.info("Analyzing " + i + " face :");
				BufferedImage originalImage = ImageIO.read(tmpFile);
				BufferedImage croppedImage = originalImage.getSubimage(Integer.valueOf(face.getPositionX()),
						Integer.valueOf(face.getPositionY()), Integer.valueOf(face.getWidth()),
						Integer.valueOf(face.getHeight()));

				ByteArrayOutputStream os = new ByteArrayOutputStream();
				ImageIO.write(croppedImage, "jpg", os);
				byte[] buffer = os.toByteArray();
				InputStream is = new ByteArrayInputStream(buffer);

				int randomNum = ThreadLocalRandom.current().nextInt(0, 10000000 + 1);

				ObjectMetadata meta = new ObjectMetadata();
				meta.setContentLength(buffer.length);
				s3Client.putObject(new PutObjectRequest("lapetusimg", "vision-" + randomNum + ".jpg", is, meta));

				imageUrls.put("vision-" + randomNum + ".jpg",
						s3Client.getUrl("lapetusimg", "vision-" + randomNum + ".jpg").toString());
				i++;
			}

			testImages(vision, s3Client);

			return Response.status(Status.OK).entity(new Gson().toJson(outputResults)).type("text/json").build();
		} catch (Exception e) {
			e.printStackTrace();
			return Response.serverError().build();
		}
	}

	public static class Result {
		public String url;
		public String name;
		public String score;

		public Result(String url, String name, String score) {
			this.url = url;
			this.name = name;
			this.score = score;
		}
	}

	private void testImages(VisualRecognition service,AmazonS3 s3Client) throws JSONException{		
		for (Map.Entry<String, String> entry : imageUrls.entrySet()) {
		    String key = entry.getKey();
		    String value = entry.getValue();
		    S3Object object = s3Client.getObject(new GetObjectRequest("lapetusimg", key));
		    InputStream objectData = object.getObjectContent();
		   
		    File tmpFile = null;
			try {
				tmpFile = File.createTempFile("vision-", ".jpg");	    
				Files.copy(objectData, tmpFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
				objectData.close(); 
			} catch (IOException e) {
				e.printStackTrace();
			}
		    
			LOGGER.info("person: " + key);
			Person person = getPerson(testImageResponse(service,tmpFile).toString());
			outputResults.add(new Result(value,person.name,person.score));
		}
	}

	private VisualClassification testImageResponse(VisualRecognition service, File file) {

		ClassifyImagesOptions options2 = new ClassifyImagesOptions.Builder().classifierIds(getClassifierId(service))
				.images(file).threshold(threshold).build();
		return service.classify(options2).execute();
	}

	private Person getPerson(String JSON_DATA) throws JSONException {
		LOGGER.info("JSON_DATA = " + JSON_DATA);
		String croppedJsonData = JSON_DATA.substring(JSON_DATA.lastIndexOf("classifiers") + "classifiers".length() + 2,
				JSON_DATA.lastIndexOf("image") - 1);
		JSONObject image = new JSONObject(new JSONArray(croppedJsonData).get(0).toString());
		JSONArray classes = image.getJSONArray("classes");
		int n = classes.length();
		String person = "None";
		Double score = 0.0;

		for (int i = 0; i < n; ++i) {
			JSONObject cls = classes.getJSONObject(i);
			LOGGER.info("Person= " + cls.getString("class") + " score=" + cls.getDouble("score"));
			if (cls.getDouble("score") > score) {
				person = cls.getString("class");
				score = cls.getDouble("score");
			}
		}
		return new Person(person, score.toString());
	}

	private String getClassifierId(VisualRecognition service) {
		List<VisualClassifier> lvc = service.getClassifiers().execute();
		LOGGER.info("classifier ID :" + lvc.get(0).getId());
		return lvc.get(0).getId();
	}

	private VisualClassifier createClassifier(VisualRecognition service) {
		ClassifierOptions.Builder classifierBuilder = new ClassifierOptions.Builder().classifierName("Person");

		File[] files = new File(trainDirPath + trainDirName).listFiles();
		for (File file : files) {
			if (file.isFile()) {
				System.out.println(file.getName().replace(".zip", ""));
				classifierBuilder.addClass(file.getName().replace(".zip", ""), file);
			}
		}
		ClassifierOptions options = classifierBuilder.build();
		VisualClassifier v1 = service.createClassifier(options).execute();
		return v1;
	}
}
