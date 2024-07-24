package econo.buddybridge.post.service;

import econo.buddybridge.matching.entity.Matching;
import econo.buddybridge.matching.entity.MatchingStatus;
import econo.buddybridge.matching.repository.MatchingRepository;
import econo.buddybridge.member.dto.MemberResDto;
import econo.buddybridge.member.entity.Member;
import econo.buddybridge.member.repository.MemberRepository;
import econo.buddybridge.post.dto.PostCustomPage;
import econo.buddybridge.post.dto.PostReqDto;
import econo.buddybridge.post.dto.PostResDto;
import econo.buddybridge.post.entity.*;
import econo.buddybridge.post.repository.PostRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;


@Service
@RequiredArgsConstructor
public class PostService {

    private final PostRepository postRepository;
    private final MemberRepository memberRepository;
    private final MatchingRepository matchingRepository;

    @Transactional(readOnly = true) // 단일 게시글
    public PostResDto findPost(Long postId) {
        Post post = postRepository.findById(postId)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 게시글입니다."));
        return postToPostRes(post);
    }

    @Transactional(readOnly = true) // 전체 게시글
    public PostCustomPage getPosts(int page, int size, String sort, PostType postType){

        Sort.Direction direction;
        try {
            direction = Sort.Direction.valueOf(sort.toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("올바르지 않은 정렬 방식입니다.");
        }

        Pageable pageable = PageRequest.of(Math.max(page-1, 0), size, Sort.by(direction,"createdAt"));
        Page<Post> postPage;

        if(postType!=null) {
            postPage = postRepository.findByPostType(pageable, postType);
        } else {
            postPage = postRepository.findAll(pageable);
        }

        List<PostResDto> postResDtoList = postPage.getContent().stream()
                .map(this::postToPostRes).collect(Collectors.toList());

        int totalPages = (int) Math.ceil((double) postPage.getTotalElements() / size);
        boolean isLast = page >= totalPages;

        return new PostCustomPage(postResDtoList, postPage.getTotalElements(), isLast);
    }
    
    // 검증 과정 필요성 고려
    @Transactional // 게시글 생성
    public Long createPost(PostReqDto postReqDto, Long memberId) {
        Member member = memberRepository.findById(memberId)
                .orElseThrow(()->new IllegalArgumentException("존재하지 않는 회원입니다."));

        Post post = postReqToPost(postReqDto,member);

        return postRepository.save(post).getId();
    }

    @Transactional // 게시글 수정
    public Long updatePost(Long postId, PostReqDto postReqDto, Long memberId){
        Post post = postRepository.findById(postId)
                .orElseThrow(()->new IllegalArgumentException("존재하지 않는 게시글입니다."));

        if(!post.getAuthor().getId().equals(memberId)) {
            throw new IllegalArgumentException("본인의 게시글만 수정할 수 있습니다.");
        }

        post.updatePost(postReqDto);

        return post.getId();
    }

    @Transactional // 게시글 삭제
    public void deletePost(Long postId, Long memberId){
        Post post = postRepository.findById(postId)
                .orElseThrow(()->new IllegalArgumentException("존재하지 않는 게시글입니다."));
        if(!post.getAuthor().getId().equals(memberId)) {
            throw new IllegalArgumentException("본인의 게시글만 수정할 수 있습니다.");
        }
        postRepository.deleteById(postId);
    }

    // Post를 사용하여 PostResDto 생성
    public PostResDto postToPostRes(Post post) {
        List<Matching> matchingList = matchingRepository.findByPostId(post.getId());
        PostStatus postStatus = determinePostStatus(matchingList);

        return PostResDto.builder()
                .id(post.getId())
                .author(toMemberResDto(post.getAuthor()))
                .title(post.getTitle())
                .assistanceType(post.getAssistanceType())
                .startTime(post.getSchedule().getStartTime())
                .endTime(post.getSchedule().getEndTime())
                .scheduleType(post.getSchedule().getScheduleType())
                .scheduleDetails(post.getSchedule().getScheduleDetails())
                .district(post.getDistrict())
                .content(post.getContent())
                .postType(post.getPostType())
                .createdAt(post.getCreatedAt())
                .modifiedAt(post.getModifiedAt())
                .postStatus(postStatus)
                .disabilityType(post.getDisabilityType())
                .build();
    }

    // 매칭 상태에 따라 게시글 상태 결정
    public PostStatus determinePostStatus(List<Matching> matchingList) {
        if (matchingList.isEmpty()) {
            return PostStatus.RECRUITING; // 모집중
        }

        boolean isCompleted = matchingList.stream()
                .anyMatch(matching -> matching.getMatchingStatus() == MatchingStatus.DONE);

        return isCompleted ? PostStatus.FINISHED : PostStatus.RECRUITING;
    }

    // PostReqDto를 바탕으로 Post생성
    public Post postReqToPost(PostReqDto postReqDto, Member member) {
        LocalDateTime startTime = postReqDto.startTime();
        LocalDateTime endTime = postReqDto.endTime();
        ScheduleType scheduleType = postReqDto.scheduleType();
        String scheduleDetails = postReqDto.scheduleDetails();

        Schedule schedule = new Schedule(startTime, endTime, scheduleType, scheduleDetails);

        return new Post(member, postReqDto.title(), postReqDto.assistanceType(), schedule, postReqDto.district(),
                postReqDto.content(), postReqDto.postType());
    }


    // 지연 로딩 시 생성되는 프록시 맴버 객체를 멤버 DTO로 변환
    public static MemberResDto toMemberResDto(Member member) {
        return MemberResDto.builder()
                .memberId(member.getId())
                .name(member.getName())
                .nickname(member.getNickname())
                .profileImageUrl(member.getProfileImageUrl())
                .email(member.getEmail())
                .age(member.getAge())
                .gender(member.getGender())
                .disabilityType(member.getDisabilityType())
                .build();
    }
}
