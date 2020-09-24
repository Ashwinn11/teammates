package teammates.e2e.util;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.Charset;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.apache.http.HttpEntity;
import org.apache.http.HttpStatus;
import org.apache.http.NameValuePair;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpDelete;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.methods.HttpPut;
import org.apache.http.client.methods.HttpRequestBase;
import org.apache.http.client.utils.URIBuilder;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.message.BasicNameValuePair;

import teammates.common.datatransfer.DataBundle;
import teammates.common.datatransfer.FeedbackParticipantType;
import teammates.common.datatransfer.attributes.CourseAttributes;
import teammates.common.datatransfer.attributes.FeedbackQuestionAttributes;
import teammates.common.datatransfer.attributes.FeedbackResponseAttributes;
import teammates.common.datatransfer.attributes.FeedbackResponseCommentAttributes;
import teammates.common.datatransfer.attributes.FeedbackSessionAttributes;
import teammates.common.datatransfer.attributes.InstructorAttributes;
import teammates.common.datatransfer.attributes.StudentAttributes;
import teammates.common.datatransfer.attributes.StudentProfileAttributes;
import teammates.common.exception.HttpRequestFailedException;
import teammates.common.util.Assumption;
import teammates.common.util.Const;
import teammates.common.util.JsonUtils;
import teammates.ui.output.CourseData;
import teammates.ui.output.CoursesData;
import teammates.ui.output.FeedbackQuestionData;
import teammates.ui.output.FeedbackQuestionsData;
import teammates.ui.output.FeedbackResponseCommentData;
import teammates.ui.output.FeedbackResponseData;
import teammates.ui.output.FeedbackResponsesData;
import teammates.ui.output.FeedbackSessionData;
import teammates.ui.output.FeedbackSessionsData;
import teammates.ui.output.FeedbackVisibilityType;
import teammates.ui.output.InstructorData;
import teammates.ui.output.InstructorsData;
import teammates.ui.output.NumberOfEntitiesToGiveFeedbackToSetting;
import teammates.ui.output.ResponseVisibleSetting;
import teammates.ui.output.SessionVisibleSetting;
import teammates.ui.output.StudentData;
import teammates.ui.request.Intent;

/**
 * Used to create API calls to the back-end without going through the UI.
 *
 * <p>Note that this will replace {@link teammates.test.BackDoor} once the front-end migration is complete.
 */
public final class BackDoor {

    private BackDoor() {
        // Utility class
    }

    /**
     * Executes GET request with the given {@code relativeUrl}.
     *
     * @return The body content and status of the HTTP response
     */
    public static ResponseBodyAndCode executeGetRequest(String relativeUrl, Map<String, String> params) {
        return executeRequest(HttpGet.METHOD_NAME, relativeUrl, params, null);
    }

    /**
     * Executes POST request with the given {@code relativeUrl}.
     *
     * @return The body content and status of the HTTP response
     */
    public static ResponseBodyAndCode executePostRequest(String relativeUrl, Map<String, String> params, String body) {
        return executeRequest(HttpPost.METHOD_NAME, relativeUrl, params, body);
    }

    /**
     * Executes PUT request with the given {@code relativeUrl}.
     *
     * @return The body content and status of the HTTP response
     */
    public static ResponseBodyAndCode executePutRequest(String relativeUrl, Map<String, String> params, String body) {
        return executeRequest(HttpPut.METHOD_NAME, relativeUrl, params, body);
    }

    /**
     * Executes DELETE request with the given {@code relativeUrl}.
     *
     * @return The body content and status of the HTTP response
     */
    public static ResponseBodyAndCode executeDeleteRequest(String relativeUrl, Map<String, String> params) {
        return executeRequest(HttpDelete.METHOD_NAME, relativeUrl, params, null);
    }

    /**
     * Executes HTTP request with the given {@code method} and {@code relativeUrl}.
     *
     * @return The content of the HTTP response
     */
    private static ResponseBodyAndCode executeRequest(
            String method, String relativeUrl, Map<String, String> params, String body) {
        String url = TestProperties.TEAMMATES_URL + relativeUrl;

        HttpRequestBase request;
        switch (method) {
        case HttpGet.METHOD_NAME:
            request = createGetRequest(url, params);
            break;
        case HttpPost.METHOD_NAME:
            request = createPostRequest(url, params, body);
            break;
        case HttpPut.METHOD_NAME:
            request = createPutRequest(url, params, body);
            break;
        case HttpDelete.METHOD_NAME:
            request = createDeleteRequest(url, params);
            break;
        default:
            throw new RuntimeException("Unaccepted HTTP method: " + method);
        }

        addAuthKeys(request);

        try (CloseableHttpClient httpClient = HttpClients.createDefault();
                CloseableHttpResponse response = httpClient.execute(request)) {

            String responseBody = null;
            HttpEntity entity = response.getEntity();
            if (entity != null) {
                try (BufferedReader br = new BufferedReader(new InputStreamReader(entity.getContent()))) {
                    responseBody = br.lines().collect(Collectors.joining(System.lineSeparator()));
                }
            }
            return new ResponseBodyAndCode(responseBody, response.getStatusLine().getStatusCode());

        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Executes GET request with the given {@code relativeUrl}.
     *
     * @return The content of the HTTP response
     */
    private static HttpGet createGetRequest(String url, Map<String, String> params) {
        return new HttpGet(createBasicUri(url, params));
    }

    private static HttpPost createPostRequest(String url, Map<String, String> params, String body) {
        HttpPost post = new HttpPost(createBasicUri(url, params));

        if (body != null) {
            StringEntity entity = new StringEntity(body, Charset.forName("UTF-8"));
            post.setEntity(entity);
        }

        return post;
    }

    private static HttpPut createPutRequest(String url, Map<String, String> params, String body) {
        HttpPut put = new HttpPut(createBasicUri(url, params));

        if (body != null) {
            StringEntity entity = new StringEntity(body, Charset.forName("UTF-8"));
            put.setEntity(entity);
        }

        return put;
    }

    private static HttpDelete createDeleteRequest(String url, Map<String, String> params) {
        return new HttpDelete(createBasicUri(url, params));
    }

    private static URI createBasicUri(String url, Map<String, String> params) {
        List<NameValuePair> postParameters = new ArrayList<>();
        if (params != null) {
            params.forEach((key, value) -> postParameters.add(new BasicNameValuePair(key, value)));
        }

        try {
            URIBuilder uriBuilder = new URIBuilder(url);
            uriBuilder.addParameters(postParameters);

            return uriBuilder.build();
        } catch (URISyntaxException e) {
            return null;
        }
    }

    private static void addAuthKeys(HttpRequestBase request) {
        request.addHeader("Backdoor-Key", TestProperties.BACKDOOR_KEY);
        request.addHeader("CSRF-Key", TestProperties.CSRF_KEY);
    }

    /**
     * Removes and restores given data in the datastore. This method is to be called on test startup.
     *
     * <p>Note:  The data associated with the test accounts have to be <strong>manually</strong> removed by removing the data
     * bundle when a test ends because the test accounts are shared across tests.
     *
     * <p>Test data should never be cleared after test in order to prevent incurring additional datastore costs because the
     * test's data may not be accessed in another test. Also although unlikely in normal conditions, when a test fail to
     * remove data bundle on teardown, another test should have no reason to fail.
     *
     * <p>Another reason not to remove associated data after a test is that in case of test failures, it helps to have the
     * associated data in the datastore to debug the failure.
     *
     * <p>This means that removing the data bundle on startup is not always sufficient because a test only knows how
     * to remove its associated data.
     * This is why some tests would fail when they use the same account and use different data.
     * Extending this method to remove data outside its associated data would introduce
     * unnecessary complications such as extra costs and knowing exactly how much data to remove. Removing too much data
     * would not just incur higher datastore costs but we can make tests unexpectedly pass(fail) when the data is expected to
     * be not present(present) in another test.
     *
     * <p>TODO: Hence, we need to explicitly remove the data bundle in tests on teardown to avoid instability of tests.
     * However, removing the data bundle on teardown manually is not a perfect solution because two tests can concurrently
     * access the same account and their data may get mixed up in the process. This is a major problem we need to address.
     */
    public static String removeAndRestoreDataBundle(DataBundle dataBundle) throws HttpRequestFailedException {
        removeDataBundle(dataBundle);
        ResponseBodyAndCode putRequestOutput =
                executePostRequest(Const.ResourceURIs.DATABUNDLE, null, JsonUtils.toJson(dataBundle));
        if (putRequestOutput.responseCode == HttpStatus.SC_OK) {
            return putRequestOutput.responseBody;
        }
        throw new HttpRequestFailedException("Request failed with status code: " + putRequestOutput.responseCode);
    }

    /**
     * Removes given data from the datastore.
     *
     * <p>If given entities have already been deleted, it fails silently.
     */
    public static void removeDataBundle(DataBundle dataBundle) {
        executePutRequest(Const.ResourceURIs.DATABUNDLE, null, JsonUtils.toJson(dataBundle));
    }

    /**
     * Puts searchable documents in data bundle into the datastore.
     */
    public static String putDocuments(DataBundle dataBundle) {
        ResponseBodyAndCode putRequestOutput =
                executePutRequest(Const.ResourceURIs.DATABUNDLE_DOCUMENTS, null, JsonUtils.toJson(dataBundle));
        return putRequestOutput.responseCode == HttpStatus.SC_OK
                ? Const.StatusCodes.BACKDOOR_STATUS_SUCCESS : Const.StatusCodes.BACKDOOR_STATUS_FAILURE;
    }

    /**
     * Gets a student's profile from the datastore.
     */
    public static StudentProfileAttributes getStudentProfile(String courseId, String studentEmail) {
        Map<String, String> params = new HashMap<>();
        params.put(Const.ParamsNames.COURSE_ID, courseId);
        params.put(Const.ParamsNames.STUDENT_EMAIL, studentEmail);
        ResponseBodyAndCode response = executeGetRequest(Const.ResourceURIs.STUDENT_PROFILE, params);
        if (response.responseCode == HttpStatus.SC_NOT_FOUND) {
            return null;
        }

        return JsonUtils.fromJson(response.responseBody, StudentProfileAttributes.class);
    }

    /**
     * Gets course data from the datastore.
     */
    public static CourseData getCourseData(String courseId) {
        Map<String, String> params = new HashMap<>();
        params.put(Const.ParamsNames.COURSE_ID, courseId);
        ResponseBodyAndCode response = executeGetRequest(Const.ResourceURIs.COURSE, params);
        if (response.responseCode == HttpStatus.SC_NOT_FOUND) {
            return null;
        }

        return JsonUtils.fromJson(response.responseBody, CourseData.class);
    }

    /**
     * Gets a course from the datastore.
     */
    public static CourseAttributes getCourse(String courseId) {
        CourseData courseData = getCourseData(courseId);
        if (courseData == null) {
            return null;
        }
        return CourseAttributes.builder(courseData.getCourseId())
                .withName(courseData.getCourseName())
                .withTimezone(ZoneId.of(courseData.getTimeZone()))
                .build();
    }

    /**
     * Gets archived course data from the datastore.
     */
    public static CourseData getArchivedCourseData(String instructorId, String courseId) {
        Map<String, String> params = new HashMap<>();
        params.put(Const.ParamsNames.USER_ID, instructorId);
        params.put(Const.ParamsNames.COURSE_ID, courseId);
        params.put(Const.ParamsNames.ENTITY_TYPE, Const.EntityType.INSTRUCTOR);
        params.put(Const.ParamsNames.COURSE_STATUS, Const.CourseStatus.ARCHIVED);

        ResponseBodyAndCode response = executeGetRequest(Const.ResourceURIs.COURSES, params);
        if (response.responseCode == HttpStatus.SC_NOT_FOUND) {
            return null;
        }

        CoursesData coursesData = JsonUtils.fromJson(response.responseBody, CoursesData.class);
        CourseData courseData = coursesData.getCourses()
                .stream()
                .filter(cd -> cd.getCourseId().equals(courseId))
                .findFirst()
                .orElse(null);

        if (courseData == null) {
            return null;
        }

        return courseData;
    }

    /**
     * Gets a archived course from the datastore.
     */
    public static CourseAttributes getArchivedCourse(String instructorId, String courseId) {
        CourseData courseData = getArchivedCourseData(instructorId, courseId);
        if (courseData == null) {
            return null;
        }
        return CourseAttributes.builder(courseData.getCourseId())
                .withName(courseData.getCourseName())
                .withTimezone(ZoneId.of(courseData.getTimeZone()))
                .build();
    }

    /**
     * Gets instructor data from the datastore.
     */
    public static InstructorData getInstructorData(String courseId, String email) {
        Map<String, String> params = new HashMap<>();
        params.put(Const.ParamsNames.COURSE_ID, courseId);
        params.put(Const.ParamsNames.INTENT, Intent.FULL_DETAIL.toString());
        ResponseBodyAndCode response = executeGetRequest(Const.ResourceURIs.INSTRUCTORS, params);
        if (response.responseCode == HttpStatus.SC_NOT_FOUND) {
            return null;
        }

        InstructorsData instructorsData = JsonUtils.fromJson(response.responseBody, InstructorsData.class);
        InstructorData instructorData = instructorsData.getInstructors()
                .stream()
                .filter(instructor -> instructor.getEmail().equals(email))
                .findFirst()
                .orElse(null);

        if (instructorData == null) {
            return null;
        }

        return instructorData;
    }

    /**
     * Get instructor from datastore. Does not include certain fields like InstructorPrivileges.
     */
    public static InstructorAttributes getInstructor(String courseId, String instructorEmail) {
        InstructorData instructorData = getInstructorData(courseId, instructorEmail);
        if (instructorData == null) {
            return null;
        }
        InstructorAttributes.Builder instructor = InstructorAttributes.builder(instructorData.getCourseId(),
                instructorData.getEmail());
        if (instructorData.getGoogleId() != null) {
            instructor.withGoogleId(instructorData.getGoogleId());
        }
        if (instructorData.getName() != null) {
            instructor.withName(instructorData.getName());
        }
        if (instructorData.getRole() != null) {
            instructor.withRole(instructorData.getRole().getRoleName());
        }
        if (instructorData.getIsDisplayedToStudents() != null) {
            instructor.withIsDisplayedToStudents(instructorData.getIsDisplayedToStudents());
        }
        if (instructorData.getDisplayedToStudentsAs() != null) {
            instructor.withDisplayedName(instructorData.getDisplayedToStudentsAs());
        }
        InstructorAttributes instructorAttributes = instructor.build();
        if (instructorData.getKey() != null) {
            instructorAttributes.key = instructorData.getKey();
        }
        return instructorAttributes;
    }

    /**
     * Gets student data from the datastore.
     */
    public static StudentData getStudentData(String courseId, String studentEmail) {
        Map<String, String> params = new HashMap<>();
        params.put(Const.ParamsNames.COURSE_ID, courseId);
        params.put(Const.ParamsNames.STUDENT_EMAIL, studentEmail);
        ResponseBodyAndCode response = executeGetRequest(Const.ResourceURIs.STUDENT, params);
        if (response.responseCode == HttpStatus.SC_NOT_FOUND) {
            return null;
        }
        return JsonUtils.fromJson(response.responseBody, StudentData.class);
    }

    /**
     * Get student from datastore.
     */
    public static StudentAttributes getStudent(String courseId, String studentEmail) {
        StudentData studentData = getStudentData(courseId, studentEmail);
        if (studentData == null) {
            return null;
        }
        StudentAttributes.Builder builder = StudentAttributes.builder(studentData.getCourseId(),
                studentData.getEmail());
        if (studentData.getGoogleId() != null) {
            builder.withGoogleId(studentData.getGoogleId());
        }
        if (studentData.getName() != null) {
            builder.withName(studentData.getName());
        }
        if (studentData.getSectionName() != null) {
            builder.withSectionName(studentData.getSectionName());
        }
        if (studentData.getTeamName() != null) {
            builder.withTeamName(studentData.getTeamName());
        }
        if (studentData.getComments() != null) {
            builder.withComment(studentData.getComments());
        }
        if (studentData.getLastName() != null) {
            builder.withLastName(studentData.getLastName());
        }
        StudentAttributes student = builder.build();
        if (studentData.getKey() != null) {
            student.key = studentData.getKey();
        }
        return student;
    }

    /**
     * Get feedback session data from datastore.
     */
    public static FeedbackSessionData getFeedbackSessionData(String courseId, String feedbackSessionName) {
        Map<String, String> params = new HashMap<>();
        params.put(Const.ParamsNames.COURSE_ID, courseId);
        params.put(Const.ParamsNames.FEEDBACK_SESSION_NAME, feedbackSessionName);
        params.put(Const.ParamsNames.INTENT, Intent.FULL_DETAIL.toString());
        ResponseBodyAndCode response = executeGetRequest(Const.ResourceURIs.SESSION, params);
        if (response.responseCode == HttpStatus.SC_NOT_FOUND) {
            return null;
        }
        return JsonUtils.fromJson(response.responseBody, FeedbackSessionData.class);
    }

    /**
     * Get feedback session from datastore.
     */
    public static FeedbackSessionAttributes getFeedbackSession(String courseId, String feedbackSessionName) {
        FeedbackSessionData sessionData = getFeedbackSessionData(courseId, feedbackSessionName);
        if (sessionData == null) {
            return null;
        }

        FeedbackSessionAttributes sessionAttributes = FeedbackSessionAttributes
                .builder(sessionData.getFeedbackSessionName(), sessionData.getCourseId())
                .withInstructions(sessionData.getInstructions())
                .withStartTime(Instant.ofEpochMilli(sessionData.getSubmissionStartTimestamp()))
                .withEndTime(Instant.ofEpochMilli(sessionData.getSubmissionEndTimestamp()))
                .withTimeZone(ZoneId.of(sessionData.getTimeZone()))
                .withGracePeriod(Duration.ofMinutes(sessionData.getGracePeriod()))
                .withIsClosingEmailEnabled(sessionData.getIsClosingEmailEnabled())
                .withIsPublishedEmailEnabled(sessionData.getIsPublishedEmailEnabled())
                .build();

        sessionAttributes.setCreatedTime(Instant.ofEpochMilli(sessionData.getCreatedAtTimestamp()));

        if (sessionData.getSessionVisibleSetting().equals(SessionVisibleSetting.AT_OPEN)) {
            sessionAttributes.setSessionVisibleFromTime(Const.TIME_REPRESENTS_FOLLOW_OPENING);
        } else {
            sessionAttributes.setSessionVisibleFromTime(Instant.ofEpochMilli(
                    sessionData.getCustomSessionVisibleTimestamp()));
        }

        if (sessionData.getResponseVisibleSetting().equals(ResponseVisibleSetting.AT_VISIBLE)) {
            sessionAttributes.setResultsVisibleFromTime(Const.TIME_REPRESENTS_FOLLOW_VISIBLE);
        } else if (sessionData.getResponseVisibleSetting().equals(ResponseVisibleSetting.LATER)) {
            sessionAttributes.setResultsVisibleFromTime(Const.TIME_REPRESENTS_LATER);
        } else {
            sessionAttributes.setResultsVisibleFromTime(Instant.ofEpochMilli(
                    sessionData.getCustomResponseVisibleTimestamp()));
        }

        return sessionAttributes;
    }

    /**
     * Get soft deleted feedback session from datastore.
     */
    public static FeedbackSessionAttributes getSoftDeletedSession(String feedbackSessionName, String instructorId) {
        Map<String, String> params = new HashMap<>();
        params.put(Const.ParamsNames.ENTITY_TYPE, Const.EntityType.INSTRUCTOR);
        params.put(Const.ParamsNames.IS_IN_RECYCLE_BIN, "true");
        params.put(Const.ParamsNames.USER_ID, instructorId);
        ResponseBodyAndCode response = executeGetRequest(Const.ResourceURIs.SESSIONS, params);
        if (response.responseCode == HttpStatus.SC_NOT_FOUND) {
            return null;
        }

        FeedbackSessionsData sessionsData = JsonUtils.fromJson(response.responseBody, FeedbackSessionsData.class);
        FeedbackSessionData feedbackSession = sessionsData.getFeedbackSessions()
                .stream()
                .filter(fs -> fs.getFeedbackSessionName().equals(feedbackSessionName))
                .findFirst()
                .orElse(null);

        if (feedbackSession == null) {
            return null;
        }

        return FeedbackSessionAttributes
                .builder(feedbackSession.getCourseId(), feedbackSession.getFeedbackSessionName())
                .build();
    }

    /**
     * Get feedback question from datastore.
     */
    public static FeedbackQuestionAttributes getFeedbackQuestion(String courseId, String feedbackSessionName,
                                                                 int qnNumber) {
        Map<String, String> params = new HashMap<>();
        params.put(Const.ParamsNames.COURSE_ID, courseId);
        params.put(Const.ParamsNames.FEEDBACK_SESSION_NAME, feedbackSessionName);
        params.put(Const.ParamsNames.INTENT, Intent.FULL_DETAIL.toString());
        ResponseBodyAndCode response = executeGetRequest(Const.ResourceURIs.QUESTIONS, params);
        if (response.responseCode == HttpStatus.SC_NOT_FOUND) {
            return null;
        }

        FeedbackQuestionsData questionsData = JsonUtils.fromJson(response.responseBody, FeedbackQuestionsData.class);
        FeedbackQuestionData question = questionsData.getQuestions()
                .stream()
                .filter(fq -> fq.getQuestionNumber() == qnNumber)
                .findFirst()
                .orElse(null);

        if (question == null) {
            return null;
        }

        FeedbackQuestionAttributes questionAttr = FeedbackQuestionAttributes.builder()
                .withCourseId(courseId)
                .withFeedbackSessionName(feedbackSessionName)
                .withQuestionDetails(question.getQuestionDetails())
                .withQuestionDescription(question.getQuestionDescription())
                .withQuestionNumber(question.getQuestionNumber())
                .withGiverType(question.getGiverType())
                .withRecipientType(question.getRecipientType())
                .withNumberOfEntitiesToGiveFeedbackTo(question.getNumberOfEntitiesToGiveFeedbackToSetting()
                        .equals(NumberOfEntitiesToGiveFeedbackToSetting.UNLIMITED)
                        ? Const.MAX_POSSIBLE_RECIPIENTS
                        : question.getCustomNumberOfEntitiesToGiveFeedbackTo())
                .withShowResponsesTo(convertToFeedbackParticipantType(question.getShowResponsesTo()))
                .withShowGiverNameTo(convertToFeedbackParticipantType(question.getShowGiverNameTo()))
                .withShowRecipientNameTo(convertToFeedbackParticipantType(question.getShowRecipientNameTo()))
                .build();
        if (question.getFeedbackQuestionId() != null) {
            questionAttr.setId(question.getFeedbackQuestionId());
        }
        return questionAttr;
    }

    /**
     * Converts List of FeedbackParticipantType to sorted List of FeedbackVisibilityType.
     */
    private static List<FeedbackParticipantType> convertToFeedbackParticipantType(
            List<FeedbackVisibilityType> feedbackVisibilityTypeList) {
        List<FeedbackParticipantType> feedbackParticipantTypeList = feedbackVisibilityTypeList.stream()
                .map(feedbackParticipantType -> {
                    switch (feedbackParticipantType) {
                    case STUDENTS:
                        return FeedbackParticipantType.STUDENTS;
                    case INSTRUCTORS:
                        return FeedbackParticipantType.INSTRUCTORS;
                    case RECIPIENT:
                        return FeedbackParticipantType.RECEIVER;
                    case GIVER_TEAM_MEMBERS:
                        return FeedbackParticipantType.OWN_TEAM_MEMBERS;
                    case RECIPIENT_TEAM_MEMBERS:
                        return FeedbackParticipantType.RECEIVER_TEAM_MEMBERS;
                    default:
                        Assumption.fail("Unknown FeedbackVisibilityType " + feedbackParticipantType);
                        break;
                    }
                    return null;
                }).collect(Collectors.toList());
        Collections.sort(feedbackParticipantTypeList);
        return feedbackParticipantTypeList;
    }

    /**
     * Get feedback response from datastore.
     */
    public static FeedbackResponseAttributes getFeedbackResponse(String feedbackQuestionId, String giver,
                                                                 String recipient) {
        Map<String, String> params = new HashMap<>();
        params.put(Const.ParamsNames.FEEDBACK_QUESTION_ID, feedbackQuestionId);
        params.put(Const.ParamsNames.INTENT, Intent.STUDENT_SUBMISSION.toString());
        params.put(Const.ParamsNames.FEEDBACK_SESSION_MODERATED_PERSON, giver);
        ResponseBodyAndCode response = executeGetRequest(Const.ResourceURIs.RESPONSES, params);
        if (response.responseCode == HttpStatus.SC_NOT_FOUND) {
            return null;
        }

        FeedbackResponsesData responsesData = JsonUtils.fromJson(response.responseBody, FeedbackResponsesData.class);
        FeedbackResponseData fr = responsesData.getResponses()
                .stream()
                .filter(r -> r.getGiverIdentifier().equals(giver) && r.getRecipientIdentifier().equals(recipient))
                .findFirst()
                .orElse(null);

        if (fr == null) {
            return null;
        }

        FeedbackResponseAttributes responseAttr = FeedbackResponseAttributes
                .builder(feedbackQuestionId, fr.getGiverIdentifier(), fr.getRecipientIdentifier())
                .withResponseDetails(fr.getResponseDetails())
                .build();
        if (fr.getFeedbackResponseId() != null) {
            responseAttr.setId(fr.getFeedbackResponseId());
        }
        return responseAttr;
    }

    /**
     * Get feedback response comment from datastore.
     */
    public static FeedbackResponseCommentAttributes getFeedbackResponseComment(String feedbackResponseId) {
        Map<String, String> params = new HashMap<>();
        params.put(Const.ParamsNames.FEEDBACK_RESPONSE_ID, feedbackResponseId);
        params.put(Const.ParamsNames.INTENT, Intent.STUDENT_SUBMISSION.toString());
        ResponseBodyAndCode response = executeGetRequest(Const.ResourceURIs.RESPONSE_COMMENT, params);
        if (response.responseCode == HttpStatus.SC_NOT_FOUND) {
            return null;
        }

        FeedbackResponseCommentData frc = JsonUtils.fromJson(response.responseBody, FeedbackResponseCommentData.class);

        if (frc == null) {
            return null;
        }

        return FeedbackResponseCommentAttributes.builder()
                .withCommentGiver(frc.getCommentGiver())
                .withCommentText(frc.getCommentText())
                .build();
    }

    /**
     * Deletes a student from the datastore.
     */
    public static void deleteStudent(String unregUserId) {
        Map<String, String> params = new HashMap<>();
        params.put(Const.ParamsNames.STUDENT_ID, unregUserId);
        executeDeleteRequest(Const.ResourceURIs.STUDENTS, params);
    }

    /**
     * Deletes a feedback session from the datastore.
     */
    public static void deleteFeedbackSession(String feedbackSession, String courseId) {
        Map<String, String> params = new HashMap<>();
        params.put(Const.ParamsNames.FEEDBACK_SESSION_NAME, feedbackSession);
        params.put(Const.ParamsNames.COURSE_ID, courseId);
        executeDeleteRequest(Const.ResourceURIs.SESSION, params);
    }

    /**
     * Deletes a course from the datastore.
     */
    public static void deleteCourse(String courseId) {
        Map<String, String> params = new HashMap<>();
        params.put(Const.ParamsNames.COURSE_ID, courseId);
        executeDeleteRequest(Const.ResourceURIs.COURSE, params);
    }

    private static final class ResponseBodyAndCode {

        String responseBody;
        int responseCode;

        ResponseBodyAndCode(String responseBody, int responseCode) {
            this.responseBody = responseBody;
            this.responseCode = responseCode;
        }

    }
}
