package com.mlreef.rest.api

import com.mlreef.rest.AccessLevel
import com.mlreef.rest.Account
import com.mlreef.rest.CodeProject
import com.mlreef.rest.CodeProjectRepository
import com.mlreef.rest.DataProject
import com.mlreef.rest.DataProjectRepository
import com.mlreef.rest.Person
import com.mlreef.rest.ProcessorVersion
import com.mlreef.rest.Project
import com.mlreef.rest.VisibilityScope
import com.mlreef.rest.api.v1.ProjectCreateRequest
import com.mlreef.rest.api.v1.ProjectUpdateRequest
import com.mlreef.rest.api.v1.ProjectUserMembershipRequest
import com.mlreef.rest.api.v1.dto.CodeProjectDto
import com.mlreef.rest.api.v1.dto.DataProjectDto
import com.mlreef.rest.api.v1.dto.ProjectDto
import com.mlreef.rest.api.v1.dto.UserInProjectDto
import com.mlreef.rest.feature.project.ProjectService
import com.mlreef.rest.feature.system.SessionsService
import com.ninjasquad.springmockk.SpykBean
import io.mockk.every
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.data.repository.findByIdOrNull
import org.springframework.restdocs.payload.FieldDescriptor
import org.springframework.restdocs.payload.JsonFieldType
import org.springframework.restdocs.payload.PayloadDocumentation
import org.springframework.restdocs.payload.PayloadDocumentation.requestFields
import org.springframework.restdocs.payload.PayloadDocumentation.responseFields
import org.springframework.restdocs.request.RequestDocumentation
import org.springframework.test.annotation.Rollback
import java.time.Instant
import java.time.Period
import java.util.UUID
import java.util.UUID.randomUUID
import javax.transaction.Transactional

class ProjectsApiTest : AbstractRestApiTest() {

    val rootUrl = "/api/v1/projects"
    val codeProjectRootUrl = "/api/v1/code-projects"
    val dataProjectRootUrl = "/api/v1/code-projects"
    private lateinit var account2: Account
    private lateinit var subject: Person
    private lateinit var subject2: Person

    @Autowired
    private lateinit var accountSubjectPreparationTrait: AccountSubjectPreparationTrait

    @Autowired
    private lateinit var pipelineTestPreparationTrait: PipelineTestPreparationTrait

    @Autowired
    private lateinit var dataProjectRepository: DataProjectRepository

    @Autowired
    private lateinit var codeProjectRepository: CodeProjectRepository

    @Autowired
    private lateinit var sessionService: SessionsService

    @SpykBean
    private lateinit var projectService: ProjectService<Project>

    @BeforeEach
    @AfterEach
    fun setUp() {
        experimentRepository.deleteAll()
        codeProjectRepository.deleteAll()
        dataProjectRepository.deleteAll()

        accountTokenRepository.deleteAll()
        accountRepository.deleteAll()
        personRepository.deleteAll()

        accountSubjectPreparationTrait.apply()

        account = accountSubjectPreparationTrait.account
        account2 = accountSubjectPreparationTrait.account2

        subject = accountSubjectPreparationTrait.subject
        subject2 = accountSubjectPreparationTrait.subject2

        // To update user permissions before each test
        sessionService.killAllSessions("username0000")
//        this.mockGetUserProjectsList(listOf(codeProject.id), account, AccessLevel.OWNER)
    }

    private fun generatePipelines(): Pair<ProcessorVersion, DataProject> {
        pipelineTestPreparationTrait.apply()
        return Pair(pipelineTestPreparationTrait.dataOp1, pipelineTestPreparationTrait.dataProject)
    }

    @Transactional
    @Rollback
    @Test
    @Tag(TestTags.RESTDOC)
    fun `Can retrieve all own DataProjects and CodeProjects only`() {
        val id1 = randomUUID()
        val id2 = randomUUID()
        val id3 = randomUUID()
        val id4 = randomUUID()
        val id5 = randomUUID()
        val id6 = randomUUID()

        //FIXME hard to maintain
        val project1 = DataProject(id1, "slug-1", "www.url.com", "Test Project 1", "description", subject.id, "group1", "project-1", 1, VisibilityScope.PUBLIC, listOf())
        val project2 = DataProject(id2, "slug-2", "www.url.net", "Test Project 2", "description", subject.id, "group2", "project-2", 2, VisibilityScope.PUBLIC, listOf())
        val project3 = DataProject(id3, "slug-3", "www.url.xyz", "Test Project 3", "description", subject2.id, "group3", "project-3", 3, VisibilityScope.PUBLIC, listOf())
        dataProjectRepository.save(project1)
        dataProjectRepository.save(project2)
        dataProjectRepository.save(project3)

        val project4 = CodeProject(id4, "slug-4", "www.url.com", "Test Code Project 1", "description", subject.id, "group4", "project-4", 1)
        val project5 = CodeProject(id5, "slug-5", "www.url.net", "Test Code Project 2", "description", subject.id, "group5", "project-5", 2)
        val project6 = CodeProject(id6, "slug-6", "www.url.xyz", "Test Code Project 3", "description", subject2.id, "group6", "project-6", 3)
        codeProjectRepository.save(project4)
        codeProjectRepository.save(project5)
        codeProjectRepository.save(project6)

        this.mockGetUserProjectsList(listOf(project1.id, project2.id, project4.id, project5.id), account, AccessLevel.OWNER)

        val returnedResult: List<DataProjectDto> = this.performGet(rootUrl, account)
            .expectOk()
            .document("project-retrieve-all", responseFields(projectResponseFields("[].")))
            .returnsList(DataProjectDto::class.java)

        assertThat(returnedResult.size).isEqualTo(4)

        val setOfIds = setOf<UUID>(
            returnedResult.get(0).id,
            returnedResult.get(1).id,
            returnedResult.get(2).id,
            returnedResult.get(3).id
        )

        assertThat(setOfIds).containsExactlyInAnyOrder(id1, id2, id4, id5)
        assertThat(returnedResult.get(0).id).isIn(id1, id2, id4, id5)
        assertThat(returnedResult.get(0).gitlabPath).isIn("project-1", "project-2", "project-4", "project-5")
        assertThat(returnedResult.get(1).id).isIn(id1, id2, id4, id5)
        assertThat(returnedResult.get(1).gitlabPath).isIn("project-1", "project-2", "project-4", "project-5")
        assertThat(returnedResult.get(2).id).isIn(id1, id2, id4, id5)
        assertThat(returnedResult.get(2).gitlabPath).isIn("project-1", "project-2", "project-4", "project-5")
        assertThat(returnedResult.get(3).id).isIn(id1, id2, id4, id5)
        assertThat(returnedResult.get(3).gitlabPath).isIn("project-1", "project-2", "project-4", "project-5")
    }

    @Transactional
    @Rollback
    @Test
    @Tag(TestTags.RESTDOC)
    fun `Can retrieve own DataProject by id`() {
        val id1 = randomUUID()
        val project1 = DataProject(id1, "slug-1", "www.url.com", "Test Data Project 1", "description", subject.id, "mlreef", "project-1", 1, VisibilityScope.PUBLIC, listOf())
        val project2 = DataProject(randomUUID(), "slug-2", "www.url.net", "Test Data Project 2", "description", subject.id, "mlreef", "project-2", 2, VisibilityScope.PUBLIC, listOf())
        val project3 = DataProject(randomUUID(), "slug-3", "www.url.xyz", "Test Data Project 3", "description", subject2.id, "mlreef", "project-3", 3, VisibilityScope.PUBLIC, listOf())
        dataProjectRepository.save(project1)
        dataProjectRepository.save(project2)
        dataProjectRepository.save(project3)

        val project4 = CodeProject(randomUUID(), "slug-4", "www.url.com", "Test Code Project 4", "description", subject.id, "group4", "project-4", 1)
        val project5 = CodeProject(randomUUID(), "slug-5", "www.url.net", "Test Code Project 5", "description", subject.id, "group5", "project-5", 2)
        val project6 = CodeProject(randomUUID(), "slug-6", "www.url.xyz", "Test Code Project 6", "description", subject2.id, "group6", "project-6", 3)
        codeProjectRepository.save(project4)
        codeProjectRepository.save(project5)
        codeProjectRepository.save(project6)

        this.mockGetUserProjectsList(listOf(project1.id, project2.id, project4.id, project5.id), account, AccessLevel.OWNER)
        val returnedResult: DataProjectDto = this.performGet("$rootUrl/$id1", account)
            .expectOk()
            .document("project-retrieve-one", responseFields(projectResponseFields()))
            .returns(DataProjectDto::class.java)

        assertThat(returnedResult.id).isEqualTo(id1)
        assertThat(returnedResult.gitlabPath).isEqualTo("project-1")
    }

    @Transactional
    @Rollback
    @Test
    @Tag(TestTags.RESTDOC)
    fun `Can retrieve own CodeProject by id`() {
        val id1 = randomUUID()
        val project1 = DataProject(randomUUID(), "slug-1", "www.url.com", "Test Data Project 1", "description", subject.id, "mlreef", "project-1", 1, VisibilityScope.PUBLIC, listOf())
        val project2 = DataProject(randomUUID(), "slug-2", "www.url.net", "Test Data Project 2", "description", subject.id, "mlreef", "project-2", 2, VisibilityScope.PUBLIC, listOf())
        val project3 = DataProject(randomUUID(), "slug-3", "www.url.xyz", "Test Data Project 3", "description", subject2.id, "mlreef", "project-3", 3, VisibilityScope.PUBLIC, listOf())
        dataProjectRepository.save(project1)
        dataProjectRepository.save(project2)
        dataProjectRepository.save(project3)

        val project4 = CodeProject(randomUUID(), "slug-4", "www.url.com", "Test Code Project 4", "description", subject.id, "group4", "project-4", 1)
        val project5 = CodeProject(id1, "slug-5", "www.url.net", "Test Code Project 5", "description", subject.id, "group5", "project-5", 2)
        val project6 = CodeProject(randomUUID(), "slug-6", "www.url.xyz", "Test Code Project 6", "description", subject2.id, "group6", "project-6", 3)
        codeProjectRepository.save(project4)
        codeProjectRepository.save(project5)
        codeProjectRepository.save(project6)

        this.mockGetUserProjectsList(listOf(project1.id, project2.id, project4.id, project5.id), account, AccessLevel.OWNER)

        val returnedResult: ProjectDto = this.performGet("$rootUrl/$id1", account)
            .expectOk()
            .document("genericprojects-retrieve-one", responseFields(projectResponseFields()))
            .returns(ProjectDto::class.java)

        assertThat(returnedResult.id).isEqualTo(id1)
        assertThat(returnedResult.gitlabPath).isEqualTo("project-5")
    }

    @Transactional
    @Rollback
    @Test
    @Tag(TestTags.RESTDOC)
    fun `Can retrieve own DataProjects and CodeProjects by slug`() {
        val id1 = randomUUID()
        val id2 = randomUUID()
        val project1 = DataProject(id1, "slug-1", "www.url.com", "Test Data Project 1", "description", subject.id, "mlreef", "project-1", 1, VisibilityScope.PUBLIC, listOf())
        val project2 = DataProject(randomUUID(), "slug-2", "www.url.net", "Test Data Project 2", "description", subject.id, "mlreef", "project-2", 2, VisibilityScope.PUBLIC, listOf())
        val project3 = DataProject(randomUUID(), "slug-3", "www.url.xyz", "Test Data Project 3", "description", subject2.id, "mlreef", "project-3", 3, VisibilityScope.PUBLIC, listOf())
        dataProjectRepository.save(project1)
        dataProjectRepository.save(project2)
        dataProjectRepository.save(project3)

        val project4 = CodeProject(randomUUID(), "slug-2", "www.url.com", "Test Code Project 4", "description", subject.id, "group4", "project-4", 1)
        val project5 = CodeProject(id2, "slug-1", "www.url.net", "Test Code Project 5", "description", subject.id, "group5", "project-5", 2)
        val project6 = CodeProject(randomUUID(), "slug-1", "www.url.xyz", "Test Code Project 6", "description", subject2.id, "group6", "project-6", 3)
        codeProjectRepository.save(project4)
        codeProjectRepository.save(project5)
        codeProjectRepository.save(project6)

        this.mockGetUserProjectsList(listOf(project1.id, project2.id, project4.id, project5.id), account, AccessLevel.OWNER)

        val returnedResult: List<ProjectDto> = this.performGet("$rootUrl/slug/slug-1", account)
            .expectOk()
            .document("project-retrieve-all", responseFields(projectResponseFields("[].")))
            .returnsList(ProjectDto::class.java)

        assertThat(returnedResult.size).isEqualTo(2)

        val setOfIds = setOf<UUID>(
            returnedResult.get(0).id,
            returnedResult.get(1).id
        )

        assertThat(setOfIds).containsExactlyInAnyOrder(id1, id2)

        assertThat(returnedResult.get(0).id).isIn(id1, id2)
        assertThat(returnedResult.get(0).gitlabPath).isIn("project-1", "project-5")
        assertThat(returnedResult.get(1).id).isIn(id1, id2)
        assertThat(returnedResult.get(1).gitlabPath).isIn("project-1", "project-5")
    }

    @Transactional
    @Rollback
    @Test
    @Tag(TestTags.RESTDOC)
    fun `Can retrieve own DataProject by namespace`() {
        val id1 = randomUUID()
        val id2 = randomUUID()
        val id3 = randomUUID()
        val id4 = randomUUID()
        val id5 = randomUUID()
        val id6 = randomUUID()

        val project1 = DataProject(id1, "slug-1", "www.url.com", "Test Project 1", "description", subject.id, "mlreef", "project-1", 1, VisibilityScope.PUBLIC, listOf())
        val project2 = DataProject(id2, "slug-2", "www.url.net", "Test Project 2", "description", subject.id, "mlreef", "project-2", 2, VisibilityScope.PUBLIC, listOf())
        val project3 = DataProject(id3, "slug-3", "www.url.xyz", "Test Project 3", "description", subject2.id, "mlreef", "project-3", 3, VisibilityScope.PUBLIC, listOf())
        dataProjectRepository.save(project1)
        dataProjectRepository.save(project2)
        dataProjectRepository.save(project3)

        val project4 = CodeProject(id4, "slug-4", "www.url.com", "Test Code Project 1", "description", subject.id, "mlreef", "project-4", 1)
        val project5 = CodeProject(id5, "slug-5", "www.url.net", "Test Code Project 2", "description", subject.id, "mlreef", "project-5", 2)
        val project6 = CodeProject(id6, "slug-6", "www.url.xyz", "Test Code Project 3", "description", subject2.id, "mlreef", "project-6", 3)
        codeProjectRepository.save(project4)
        codeProjectRepository.save(project5)
        codeProjectRepository.save(project6)


        this.mockGetUserProjectsList(listOf(project1.id, project2.id, project4.id, project5.id), account, AccessLevel.OWNER)

        val returnedResult: List<ProjectDto> = this.performGet("$rootUrl/namespace/mlreef", account)
            .expectOk()
            .document("project-retrieve-all", responseFields(projectResponseFields("[].")))
            .returnsList(ProjectDto::class.java)

        assertThat(returnedResult.size).isEqualTo(4)

        val setOfIds = setOf<UUID>(
            returnedResult.get(0).id,
            returnedResult.get(1).id,
            returnedResult.get(2).id,
            returnedResult.get(3).id
        )

        assertThat(setOfIds).containsExactlyInAnyOrder(id1, id2, id4, id5)
        assertThat(returnedResult.get(0).id).isIn(id1, id2, id4, id5)
        assertThat(returnedResult.get(0).gitlabPath).isIn("project-1", "project-2", "project-4", "project-5")
        assertThat(returnedResult.get(1).id).isIn(id1, id2, id4, id5)
        assertThat(returnedResult.get(1).gitlabPath).isIn("project-1", "project-2", "project-4", "project-5")
        assertThat(returnedResult.get(2).id).isIn(id1, id2, id4, id5)
        assertThat(returnedResult.get(2).gitlabPath).isIn("project-1", "project-2", "project-4", "project-5")
        assertThat(returnedResult.get(3).id).isIn(id1, id2, id4, id5)
        assertThat(returnedResult.get(3).gitlabPath).isIn("project-1", "project-2", "project-4", "project-5")
    }

    @Transactional
    @Rollback
    @Test
    @Tag(TestTags.RESTDOC)
    fun `Can retrieve own DataProject by namespace and slug`() {
        val id1 = randomUUID()
        val project1 = DataProject(id1, "slug-1", "www.url.com", "Test Data Project 1", "description", subject.id, "mlreef", "project-1", 1, VisibilityScope.PUBLIC, listOf())
        val project2 = DataProject(randomUUID(), "slug-2", "www.url.net", "Test Data Project 2", "description", subject.id, "mlreef", "project-2", 2, VisibilityScope.PUBLIC, listOf())
        val project3 = DataProject(randomUUID(), "slug-3", "www.url.xyz", "Test Data Project 3", "description", subject2.id, "mlreef", "project-3", 3, VisibilityScope.PUBLIC, listOf())
        dataProjectRepository.save(project1)
        dataProjectRepository.save(project2)
        dataProjectRepository.save(project3)

        val project4 = CodeProject(randomUUID(), "slug-4", "www.url.com", "Test Code Project 4", "description", subject.id, "group4", "project-4", 4)
        val project5 = CodeProject(randomUUID(), "slug-5", "www.url.net", "Test Code Project 5", "description", subject.id, "group5", "project-5", 5)
        val project6 = CodeProject(randomUUID(), "slug-6", "www.url.xyz", "Test Code Project 6", "description", subject2.id, "group6", "project-6", 6)
        codeProjectRepository.save(project4)
        codeProjectRepository.save(project5)
        codeProjectRepository.save(project6)

        this.mockGetUserProjectsList(listOf(project1.id, project2.id, project4.id, project5.id), account, AccessLevel.OWNER)

        val returnedResult: ProjectDto = this.performGet("$rootUrl/mlreef/project-1", account)
            .expectOk()
            .document("project-retrieve-one", responseFields(projectResponseFields()))
            .returns(ProjectDto::class.java)

        assertThat(returnedResult.id).isEqualTo(id1)
        assertThat(returnedResult.gitlabPath).isEqualTo("project-1")
    }

    @Transactional
    @Rollback
    @Test
    @Tag(TestTags.RESTDOC)
    fun `Can retrieve own CodeProject by namespace and slug`() {
        val id1 = randomUUID()
        val project1 = DataProject(randomUUID(), "slug-1", "www.url.com", "Test Data Project 1", "description", subject.id, "mlreef", "project-1", 1, VisibilityScope.PUBLIC, listOf())
        val project2 = DataProject(randomUUID(), "slug-2", "www.url.net", "Test Data Project 2", "description", subject.id, "mlreef", "project-2", 2, VisibilityScope.PUBLIC, listOf())
        val project3 = DataProject(randomUUID(), "slug-3", "www.url.xyz", "Test Data Project 3", "description", subject2.id, "mlreef", "project-3", 3, VisibilityScope.PUBLIC, listOf())
        dataProjectRepository.save(project1)
        dataProjectRepository.save(project2)
        dataProjectRepository.save(project3)

        val project4 = CodeProject(randomUUID(), "slug-4", "www.url.com", "Test Code Project 4", "description", subject.id, "mlreef", "project-4", 4)
        val project5 = CodeProject(id1, "slug-5", "www.url.net", "Test Code Project 5", "description", subject.id, "mlreef", "project-5", 5)
        val project6 = CodeProject(randomUUID(), "slug-6", "www.url.xyz", "Test Code Project 6", "description", subject2.id, "mlreef", "project-6", 6)
        codeProjectRepository.save(project4)
        codeProjectRepository.save(project5)
        codeProjectRepository.save(project6)

        this.mockGetUserProjectsList(listOf(project1.id, project2.id, project4.id, project5.id), account, AccessLevel.OWNER)

        val returnedResult: ProjectDto = this.performGet("$rootUrl/mlreef/project-5", account)
            .expectOk()
            .document("project-retrieve-one", responseFields(projectResponseFields()))
            .returns(ProjectDto::class.java)

        assertThat(returnedResult.id).isEqualTo(id1)
        assertThat(returnedResult.gitlabPath).isEqualTo("project-5")
    }

    @Transactional
    @Rollback
    @Test
    @Tag(TestTags.RESTDOC)
    fun `Cannot retrieve not own Project`() {
        val id1 = randomUUID()
        val project1 = DataProject(randomUUID(), "slug-1", "www.url.com", "Test Data Project 1", "description", subject.id, "mlreef", "project-1", 1, VisibilityScope.PUBLIC, listOf())
        val project2 = DataProject(randomUUID(), "slug-2", "www.url.net", "Test Data Project 2", "description", subject.id, "mlreef", "project-2", 2, VisibilityScope.PUBLIC, listOf())
        val project3 = DataProject(randomUUID(), "slug-3", "www.url.xyz", "Test Data Project 3", "description", subject2.id, "mlreef", "project-3", 3, VisibilityScope.PUBLIC, listOf())
        dataProjectRepository.save(project1)
        dataProjectRepository.save(project2)
        dataProjectRepository.save(project3)

        val project4 = CodeProject(randomUUID(), "slug-4", "www.url.com", "Test Code Project 4", "description", subject.id, "group4", "project-4", 1)
        val project5 = CodeProject(randomUUID(), "slug-5", "www.url.net", "Test Code Project 5", "description", subject.id, "group5", "project-5", 2)
        val project6 = CodeProject(id1, "slug-6", "www.url.xyz", "Test Code Project 6", "description", subject2.id, "group6", "project-6", 3)
        codeProjectRepository.save(project4)
        codeProjectRepository.save(project5)
        codeProjectRepository.save(project6)

        this.mockGetUserProjectsList(listOf(project1.id, project2.id, project4.id, project5.id), account, AccessLevel.OWNER)

        this.performGet("$rootUrl/$id1")
            .expectForbidden()
    }

    @Transactional
    @Rollback
    @Test
    @Tag(TestTags.RESTDOC)
    fun `Can create CodeProject`() {
        val request = ProjectCreateRequest(
            slug = "test-project",
            namespace = "mlreef",
            name = "Test project",
            description = "Description of Test Project",
            visibility = VisibilityScope.PUBLIC,
            initializeWithReadme = true
        )

        this.mockGetUserProjectsList(account)

        val returnedResult = this.performPost("$rootUrl/code", account, body = request)
            .expectOk()
            .document("project-create",
                requestFields(projectCreateRequestFields()),
                responseFields(projectResponseFields())
            )
            .returns(CodeProjectDto::class.java)

        assertThat(returnedResult).isNotNull()
    }

    @Transactional
    @Rollback
    @Test
    @Tag(TestTags.RESTDOC)
    fun `Can create CodeProject by code-project path`() {
        val request = ProjectCreateRequest(
            slug = "test-project",
            namespace = "mlreef",
            name = "Test project",
            description = "Description of Test Project",
            visibility = VisibilityScope.PUBLIC,
            initializeWithReadme = true
        )

        this.mockGetUserProjectsList(account)

        val returnedResult = this.performPost("$codeProjectRootUrl", account, body = request)
            .expectOk()
            .document("project-create",
                requestFields(projectCreateRequestFields()),
                responseFields(projectResponseFields())
            )
            .returns(CodeProjectDto::class.java)

        assertThat(returnedResult).isNotNull()
    }

    @Transactional
    @Rollback
    @Tag(TestTags.RESTDOC)
    @Test
    fun `Can create DataProject`() {
        val request = ProjectCreateRequest(
            "test-project",
            "mlreef",
            "Test project",
            "description",
            true,
            VisibilityScope.PUBLIC
        )

        this.mockGetUserProjectsList(account)

        val returnedResult = this.performPost("$rootUrl/data", account, body = request)
            .expectOk()
            .document("project-create",
                requestFields(projectCreateRequestFields()),
                responseFields(projectResponseFields()))
            .returns(DataProjectDto::class.java)

        assertThat(returnedResult).isNotNull
    }

    @Transactional
    @Rollback
    @Tag(TestTags.RESTDOC)
    @Test
    fun `Can create DataProject by data-project path`() {
        val request = ProjectCreateRequest(
            "test-project",
            "mlreef",
            "Test project",
            "description",
            true,
            VisibilityScope.PUBLIC
        )

        this.mockGetUserProjectsList(account)

        val returnedResult = this.performPost("$dataProjectRootUrl", account, body = request)
            .expectOk()
            .document("project-create",
                requestFields(projectCreateRequestFields()),
                responseFields(projectResponseFields()))
            .returns(DataProjectDto::class.java)

        assertThat(returnedResult).isNotNull
    }

    @Transactional
    @Rollback
    @Test
    @Tag(TestTags.RESTDOC)
    fun `Can update own CodeProject`() {
        val id1 = randomUUID()
        val project1 = CodeProject(id1, "slug-1", "www.url.com", "Test Project 1", "description", account.person.id, "group1", "project-1", 1)
        codeProjectRepository.save(project1)

        mockGetUserProjectsList(listOf(project1.id), account, AccessLevel.OWNER)
        mockGitlabUpdateProject()

        val request = ProjectUpdateRequest("New Test project", "new description")

        val returnedResult = this.performPut("$rootUrl/$id1", account, body = request)
            .expectOk()
            .document("project-update",
                requestFields(projectUpdateRequestFields()),
                responseFields(projectResponseFields())
            )
            .returns(ProjectDto::class.java)

        assertThat(returnedResult.name).isEqualTo("New Test project")
    }

    @Transactional
    @Rollback
    @Test
    @Tag(TestTags.RESTDOC)
    fun `Can update own DataProject`() {
        val id1 = randomUUID()
        val project1 = DataProject(id1, "slug-1", "www.url.com", "Test Project 1", "description", subject.id, "mlreef", "project-1", 1, VisibilityScope.PUBLIC, listOf())
        dataProjectRepository.save(project1)

        this.mockGetUserProjectsList(listOf(project1.id), account, AccessLevel.OWNER)
        this.mockGitlabUpdateProject()

        val request = ProjectUpdateRequest("New Test project", "description")

        val returnedResult = this.performPut("$rootUrl/$id1", account, body = request)
            .expectOk()
            .document("project-update",
                requestFields(projectUpdateRequestFields()),
                responseFields(projectResponseFields()))
            .returns(ProjectDto::class.java)

        assertThat(returnedResult.name).isEqualTo("New Test project")
    }

    @Transactional
    @Rollback
    @Test
    @Tag(TestTags.RESTDOC)
    fun `Can delete own DataProject`() {
        val id1 = randomUUID()
        val project1 = DataProject(id1, "slug-1", "www.url.com", "Test Project 1", "description", subject.id, "mlreef", "group1", 1, VisibilityScope.PUBLIC, listOf())
        dataProjectRepository.save(project1)

        this.mockGetUserProjectsList(listOf(project1.id), account, AccessLevel.OWNER)

        assertThat(dataProjectRepository.findByIdOrNull(id1)).isNotNull()

        this.performDelete("$rootUrl/$id1", account)
            .expectNoContent()
            .document("project-delete")

        assertThat(dataProjectRepository.findByIdOrNull(id1)).isNull()
    }

    @Transactional
    @Rollback
    @Test
    @Tag(TestTags.RESTDOC)
    fun `Can delete own CodeProject`() {
        val id1 = randomUUID()
        val project1 = CodeProject(id1, "slug-1", "www.url.com", "Test Project 1", "description", account.person.id, "group1", "project-1", 1)
        codeProjectRepository.save(project1)

        mockGetUserProjectsList(listOf(project1.id), account, AccessLevel.OWNER)

        assertThat(codeProjectRepository.findByIdOrNull(id1)).isNotNull()

        this.performDelete("$rootUrl/$id1", account)
            .expectNoContent()
            .document("project-delete")

        assertThat(codeProjectRepository.findByIdOrNull(id1)).isNull()
    }

    @Transactional
    @Rollback
    @Test
    @Tag(TestTags.RESTDOC)
    fun `Can retrieve users list in DataProject`() {
        val id1 = randomUUID()
        val project1 = DataProject(id1, "slug-1", "www.url.com", "Test Project 1", "100 tests", randomUUID(), "mlreef", "group1", 1, VisibilityScope.PUBLIC, listOf())
        dataProjectRepository.save(project1)

        every { projectService.getUsersInProject(any()) } answers {
            listOf(account, account2).map { accountToUserInProject(it) }
        }

        this.mockGetUserProjectsList(listOf(project1.id), account, AccessLevel.OWNER)
        this.mockGetUserProjectsList(listOf(project1.id), account2, AccessLevel.DEVELOPER)

        val returnedResult: List<UserInProjectDto> = this.performGet("$rootUrl/${project1.id}/users", account)
            .expectOk()
            .document("project-retrieve-users-list", responseFields(usersInProjectResponseFields("[].")))
            .returnsList(UserInProjectDto::class.java)

        assertThat(returnedResult.size).isEqualTo(2)
    }

    @Transactional
    @Rollback
    @Test
    @Tag(TestTags.RESTDOC)
    fun `Can add user to CodeProject by userId in path`() {
        val id1 = randomUUID()
        val project1 = CodeProject(id1, "slug-1", "www.url.com", "Test Project 1", "", account2.person.id, "group1", "project-1", 1)
        codeProjectRepository.save(project1)

        every { projectService.getUsersInProject(any()) } answers {
            listOf(account, account2).map { accountToUserInProject(it) }
        }

        this.mockGetUserProjectsList(listOf(project1.id), account, AccessLevel.OWNER)

        val returnedResult: List<UserInProjectDto> = this.performPost("$rootUrl/${project1.id}/users/${account2.id}", account)
            .expectOk()
            .document("projects-add-user", responseFields(usersInProjectResponseFields("[].")))
            .returnsList(UserInProjectDto::class.java)

        assertThat(returnedResult.size).isEqualTo(2)
    }

    @Transactional
    @Rollback
    @Test
    @Tag(TestTags.RESTDOC)
    fun `Can add user to DataProject by userId in path`() {
        val id1 = randomUUID()
        val project1 = DataProject(id1, "slug-1", "www.url.com", "Test Project 1", "100 tests", randomUUID(), "mlreef", "group1", 1, VisibilityScope.PUBLIC, listOf())
        dataProjectRepository.save(project1)

        every { projectService.getUsersInProject(any()) } answers {
            listOf(account, account2).map { accountToUserInProject(it) }
        }

        this.mockGetUserProjectsList(listOf(project1.id), account, AccessLevel.OWNER)

        val returnedResult: List<UserInProjectDto> = this.performPost("$rootUrl/${project1.id}/users/${account2.id}", account)
            .expectOk()
            .document("dataprojects-add-user", responseFields(usersInProjectResponseFields("[].")))
            .returnsList(UserInProjectDto::class.java)

        assertThat(returnedResult.size).isEqualTo(2)
    }

    @Transactional
    @Rollback
    @Test
    @Tag(TestTags.RESTDOC)
    fun `Can add user to CodeProject by gitlabId in params`() {
        val id1 = randomUUID()
        val project1 = CodeProject(id1, "slug-1", "www.url.com", "Test Project 1", "", account2.person.id, "group1", "project-1", 1)
        codeProjectRepository.save(project1)

        every { projectService.getUsersInProject(any()) } answers {
            listOf(account, account2).map { accountToUserInProject(it) }
        }

        this.mockGetUserProjectsList(listOf(project1.id), account, AccessLevel.OWNER)

        val url = "$rootUrl/${project1.id}/users?gitlab_id=${account2.person.gitlabId}&level=DEVELOPER&expires_at=2099-12-31T10:15:20Z"

        val returnedResult: List<UserInProjectDto> = this.performPost(url, account)
            .expectOk()
            .document("project-add-user-by-params",
                RequestDocumentation.requestParameters(
                    RequestDocumentation.parameterWithName("user_id").optional().description("Internal User id - UUID"),
                    RequestDocumentation.parameterWithName("gitlab_id").optional().description("Gitlab user id - Number"),
                    RequestDocumentation.parameterWithName("level").optional().description("Level/role of user in project"),
                    RequestDocumentation.parameterWithName("expires_at").optional().description("Date of access expiration in ISO format (not passed value means unlimited access)")
                ),
                responseFields(usersInProjectResponseFields("[].")))
            .returnsList(UserInProjectDto::class.java)

        assertThat(returnedResult.size).isEqualTo(2)
    }

    @Transactional
    @Rollback
    @Test
    fun `Can add user to DataProject by gitlabId in param`() {
        val id1 = randomUUID()
        val project1 = DataProject(id1, "slug-1", "www.url.com", "Test Project 1", "100 tests", randomUUID(), "mlreef", "group1", 1, VisibilityScope.PUBLIC, listOf())
        dataProjectRepository.save(project1)

        every { projectService.getUsersInProject(any()) } answers {
            listOf(account, account2).map { accountToUserInProject(it) }
        }

        this.mockGetUserProjectsList(listOf(project1.id), account, AccessLevel.OWNER)

        val url = "$rootUrl/${project1.id}/users?gitlab_id=${account2.person.gitlabId}&level=DEVELOPER&expires_at=2099-12-31T10:15:20Z"

        val returnedResult: List<UserInProjectDto> = this.performPost(url, account)
            .expectOk()
            .document("project-add-user-by-params",
                RequestDocumentation.requestParameters(
                    RequestDocumentation.parameterWithName("user_id").optional().description("Internal User id - UUID"),
                    RequestDocumentation.parameterWithName("gitlab_id").optional().description("Gitlab user id - Number"),
                    RequestDocumentation.parameterWithName("level").optional().description("Level/role of user in project"),
                    RequestDocumentation.parameterWithName("expires_at").optional().description("Date of access expiration in ISO format (not passed value means unlimited access)")
                ),
                responseFields(usersInProjectResponseFields("[].")))
            .returnsList(UserInProjectDto::class.java)

        assertThat(returnedResult.size).isEqualTo(2)
    }

    @Transactional
    @Rollback
    @Test
    fun `Can add user to DataProject by gitlabId in body`() {
        val id1 = randomUUID()
        val project1 = DataProject(id1, "slug-1", "www.url.com", "Test Project 1", "", account2.person.id, "group1", "project-1", 1)
        dataProjectRepository.save(project1)

        every { projectService.getUsersInProject(any()) } answers {
            listOf(account, account2).map { accountToUserInProject(it) }
        }

        this.mockGetUserProjectsList(listOf(project1.id), account, AccessLevel.OWNER)

        val request = ProjectUserMembershipRequest(userId = account2.id, gitlabId = 10, level = "REPORTER", expiresAt = Instant.now().plus(Period.ofDays(1)))

        val url = "$rootUrl/${project1.id}/users"

        val returnedResult: List<UserInProjectDto> = this.performPost(url, account, request)
            .expectOk()
            .document("project-add-user-by-body",
                requestFields(projectAddEditUserRequestFields()),
                responseFields(usersInProjectResponseFields("[].")))
            .returnsList(UserInProjectDto::class.java)

        assertThat(returnedResult.size).isEqualTo(2)
    }

    @Transactional
    @Rollback
    @Test
    fun `Can add user to CodeProject by gitlabId in body`() {
        val id1 = randomUUID()
        val project1 = CodeProject(id1, "slug-1", "www.url.com", "Test Project 1", "", account2.person.id, "group1", "project-1", 1)
        codeProjectRepository.save(project1)

        every { projectService.getUsersInProject(any()) } answers {
            listOf(account, account2).map { accountToUserInProject(it) }
        }

        this.mockGetUserProjectsList(listOf(project1.id), account, AccessLevel.OWNER)

        val request = ProjectUserMembershipRequest(userId = account2.id, gitlabId = 10, level = "REPORTER", expiresAt = Instant.now().plus(Period.ofDays(1)))

        val url = "$rootUrl/${project1.id}/users"

        val returnedResult: List<UserInProjectDto> = this.performPost(url, account, request)
            .expectOk()
            .document("project-add-user-by-body",
                requestFields(projectAddEditUserRequestFields()),
                responseFields(usersInProjectResponseFields("[].")))
            .returnsList(UserInProjectDto::class.java)

        assertThat(returnedResult.size).isEqualTo(2)
    }

    @Transactional
    @Rollback
    @Test
    fun `Can delete user from DataProject by userId in path`() {
        val id1 = randomUUID()
        val project1 = DataProject(id1, "slug-1", "www.url.com", "Test Project 1", "100 tests", randomUUID(), "mlreef", "group1", 1, VisibilityScope.PUBLIC, listOf())
        dataProjectRepository.save(project1)

        every { projectService.getUsersInProject(any()) } answers {
            listOf(account).map { accountToUserInProject(it) }
        }

        this.mockGetUserProjectsList(listOf(project1.id), account, AccessLevel.OWNER)

        val returnedResult: List<UserInProjectDto> = this.performDelete("$rootUrl/${project1.id}/users/${account2.id}", account)
            .expectOk()
            .document("projects-delete-user", responseFields(usersInProjectResponseFields("[].")))
            .returnsList(UserInProjectDto::class.java)

        assertThat(returnedResult.size).isEqualTo(1)
    }

    @Transactional
    @Rollback
    @Test
    fun `Can delete user from CodeProject by userId in path`() {
        val id1 = randomUUID()
        val project1 = CodeProject(id1, "slug-1", "www.url.com", "Test Project 1", "", account2.person.id, "group1", "project-1", 1)
        codeProjectRepository.save(project1)

        every { projectService.getUsersInProject(any()) } answers {
            listOf(account).map { accountToUserInProject(it) }
        }

        this.mockGetUserProjectsList(listOf(project1.id), account, AccessLevel.OWNER)

        val returnedResult: List<UserInProjectDto> = this.performDelete("$rootUrl/${project1.id}/users/${account2.id}", account)
            .expectOk()
            .document("project-delete-user", responseFields(usersInProjectResponseFields("[].")))
            .returnsList(UserInProjectDto::class.java)

        assertThat(returnedResult.size).isEqualTo(1)
    }

    @Transactional
    @Rollback
    @Test
    fun `Can delete user from DataProject by gitlabId in param`() {
        val id1 = randomUUID()
        val project1 = DataProject(id1, "slug-1", "www.url.com", "Test Project 1", "100 tests", randomUUID(), "mlreef", "group1", 1, VisibilityScope.PUBLIC, listOf())
        dataProjectRepository.save(project1)

        every { projectService.getUsersInProject(any()) } answers {
            listOf(account).map { accountToUserInProject(it) }
        }

        this.mockGetUserProjectsList(listOf(project1.id), account, AccessLevel.OWNER)

        val url = "$rootUrl/${project1.id}/users?gitlab_id=${account2.person.gitlabId}"

        val returnedResult: List<UserInProjectDto> = this.performDelete(url, account)
            .expectOk()
            .document("project-delete-user-by-params",
                RequestDocumentation.requestParameters(
                    RequestDocumentation.parameterWithName("user_id").optional().description("Internal User id - UUID"),
                    RequestDocumentation.parameterWithName("gitlab_id").optional().description("Gitlab user id - Number")
                ),
                responseFields(usersInProjectResponseFields("[].")))
            .returnsList(UserInProjectDto::class.java)

        assertThat(returnedResult.size).isEqualTo(1)
    }

    @Transactional
    @Rollback
    @Test
    fun `Can delete user from CodeProject by gitlabId in param`() {
        val id1 = randomUUID()
        val project1 = CodeProject(id1, "slug-1", "www.url.com", "Test Project 1", "", account2.person.id, "group1", "project-1", 1)
        codeProjectRepository.save(project1)

        every { projectService.getUsersInProject(any()) } answers {
            listOf(account).map { accountToUserInProject(it) }
        }

        this.mockGetUserProjectsList(listOf(project1.id), account, AccessLevel.OWNER)

        val url = "$rootUrl/${project1.id}/users?gitlab_id=${account2.person.gitlabId}"

        val returnedResult: List<UserInProjectDto> = this.performDelete(url, account)
            .expectOk()
            .document("project-delete-user-by-params",
                RequestDocumentation.requestParameters(
                    RequestDocumentation.parameterWithName("user_id").optional().description("Internal User id - UUID"),
                    RequestDocumentation.parameterWithName("gitlab_id").optional().description("Gitlab user id - Number")
                ),
                responseFields(usersInProjectResponseFields("[].")))
            .returnsList(UserInProjectDto::class.java)

        assertThat(returnedResult.size).isEqualTo(1)
    }

    private fun projectCreateRequestFields(): List<FieldDescriptor> {
        return listOf(
            PayloadDocumentation.fieldWithPath("slug").type(JsonFieldType.STRING).description("Valid slug of Project (matches Gitlab)"),
            PayloadDocumentation.fieldWithPath("namespace").type(JsonFieldType.STRING).description("Gitlab group or user namespace"),
            PayloadDocumentation.fieldWithPath("name").type(JsonFieldType.STRING).description("Name of Project"),
            PayloadDocumentation.fieldWithPath("description").type(JsonFieldType.STRING).description("Description of Project"),
            PayloadDocumentation.fieldWithPath("initialize_with_readme").type(JsonFieldType.BOOLEAN).description("Boolean flag, if that Project should have an automatic commit for a README"),
            PayloadDocumentation.fieldWithPath("visibility").type(JsonFieldType.STRING).description("Visibility, can be 'PUBLIC', 'INTERNAL', 'PRIVATE'"),
            PayloadDocumentation.fieldWithPath("experiments").type(JsonFieldType.OBJECT).optional().description("Experiments arrays")
        )
    }

    private fun projectUpdateRequestFields(): List<FieldDescriptor> {
        return listOf(
            PayloadDocumentation.fieldWithPath("description").type(JsonFieldType.STRING).description("Description of Project"),
            PayloadDocumentation.fieldWithPath("name").type(JsonFieldType.STRING).description("Name of Project"),
            PayloadDocumentation.fieldWithPath("visibility").type(JsonFieldType.STRING).optional().description("Visibility of project"),
            PayloadDocumentation.fieldWithPath("input_data_types").type(JsonFieldType.STRING).optional().description("Input data types"),
            PayloadDocumentation.fieldWithPath("output_data_types").type(JsonFieldType.STRING).optional().description("Output data types"),
            PayloadDocumentation.fieldWithPath("tags").type(JsonFieldType.STRING).optional().description("Tags")
        )
    }

    fun usersInProjectResponseFields(prefix: String = ""): List<FieldDescriptor> {
        return listOf(
            PayloadDocumentation.fieldWithPath(prefix + "id").type(JsonFieldType.STRING).description("Data project id"),
            PayloadDocumentation.fieldWithPath(prefix + "user_name").type(JsonFieldType.STRING).description("User name"),
            PayloadDocumentation.fieldWithPath(prefix + "email").type(JsonFieldType.STRING).description("User's email"),
            PayloadDocumentation.fieldWithPath(prefix + "gitlab_id").type(JsonFieldType.NUMBER).description("Id in gitlab"),
            PayloadDocumentation.fieldWithPath(prefix + "access_level").type(JsonFieldType.STRING).description("Role"),
            PayloadDocumentation.fieldWithPath(prefix + "expired_at").type(JsonFieldType.STRING).optional().description("Access expires at")
        )
    }

    fun projectAddEditUserRequestFields(): List<FieldDescriptor> {
        return listOf(
            PayloadDocumentation.fieldWithPath("user_id").type(JsonFieldType.STRING).optional().description("User id"),
            PayloadDocumentation.fieldWithPath("gitlab_id").type(JsonFieldType.NUMBER).optional().description("Gitlab user id"),
            PayloadDocumentation.fieldWithPath("level").type(JsonFieldType.STRING).optional().description("Role/Level of user in project"),
            PayloadDocumentation.fieldWithPath("expires_at").type(JsonFieldType.STRING).optional().description("Expiration date")
        )
    }

}