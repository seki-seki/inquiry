package jp.co.tis.adc.inquiry;

import java.util.HashMap;

import com.amazonaws.regions.Region;
import com.amazonaws.regions.Regions;
import com.amazonaws.services.dynamodbv2.AmazonDynamoDBClient;
import com.amazonaws.services.dynamodbv2.document.DynamoDB;
import com.amazonaws.services.dynamodbv2.document.Item;
import com.amazonaws.services.dynamodbv2.document.Table;
import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.simpleemail.AmazonSimpleEmailServiceClient;
import com.amazonaws.services.simpleemail.model.Body;
import com.amazonaws.services.simpleemail.model.Content;
import com.amazonaws.services.simpleemail.model.Destination;
import com.amazonaws.services.simpleemail.model.Message;
import com.amazonaws.services.simpleemail.model.SendEmailRequest;

import jp.co.tis.adc.inquiry.entity.Request;

public class LambdaFunctionHandler implements RequestHandler<Request, Object> {

    public Object handleRequest(Request input, Context context) {
        try {
            // 1. API Gateway からのリクエストを受け取り、リクエスト中の name, email, messageを抽出する
            System.out.println(input);
            String name = (String) input.getName();
            String email = (String) input.getEmail();
            String message = (String) input.getMessage();
            System.out.println("Loaded request");
            // 2. DynamoDB 上の sequences テーブルで name="inquiry" である項目の "current"
            // を抽出し、インクリメントする
            AmazonDynamoDBClient dbclient = new AmazonDynamoDBClient();
            dbclient.withRegion(Regions.US_WEST_1); // DynamoDBのリージョンはカリフォルニア
            DynamoDB dynamoDB = new DynamoDB(dbclient);
            Table sequences = dynamoDB.getTable("sequences");
            sequences.updateItem("name", "inquiry", "set #c = #c + :c", 
                    new HashMap() {{put("#c", "current");}},new HashMap() {{put(":c", 1);}});
            Item sequenceItem = sequences.getItem("name", "inquiry");
            int inquiryCurrentID = sequenceItem.getInt("current");
            System.out.println("Incrimented id");
            // 3. DynamoDB 上の inquiry テーブルに下記のデータを登録する
            Table inquiry = dynamoDB.getTable("inquiry");
            inquiry.putItem(new Item().withPrimaryKey("id", inquiryCurrentID).with("name", name).with("email", email)
                    .with("message", message));
            System.out.println("Put data");
            // 4. SES を利用して下記の内容のメールを送信する
            Destination destination = new Destination().withToAddresses(new String[] { email });
            Content subject = new Content().withData("Your inquiry was accepted.");
            Content textBody = new Content().withData(message);
            Body body = new Body().withText(textBody);
            Message emailMessage = new Message().withSubject(subject).withBody(body);
            SendEmailRequest request = new SendEmailRequest().withSource("seki.yuki@tis.co.jp")
                    .withDestination(destination).withMessage(emailMessage);
            AmazonSimpleEmailServiceClient emailClient = new AmazonSimpleEmailServiceClient();
            emailClient.setRegion(Region.getRegion(Regions.US_WEST_2));
            emailClient.sendEmail(request);
            System.out.println("Sent email");
        } catch (final Throwable e) {
            e.printStackTrace();
            // handler関数の返り値は、"statusCode", "body" というキーを含んだ HasnMap
            return new HashMap() {
                {
                    put("statusCode", 500);
                    put("body", "Internal server error:" + e.getMessage());
                }
            };
        }
        // handler関数の返り値は、"statusCode", "body" というキーを含んだ HasnMap
        return new HashMap() {
            {
                put("statusCode", 201);
                put("body", "Created");
            }
        };
    }

}
