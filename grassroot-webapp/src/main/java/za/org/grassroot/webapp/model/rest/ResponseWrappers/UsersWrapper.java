package za.org.grassroot.webapp.model.rest.ResponseWrappers;

import com.fasterxml.jackson.annotation.JsonInclude;
import org.springframework.data.domain.Page;
import za.org.grassroot.core.domain.User;
import za.org.grassroot.core.dto.UserDTO;

import java.util.ArrayList;
import java.util.List;

/**
 * Created by paballo on 2016/03/24.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class UsersWrapper {

    private List<UserDTO> members;
    private Integer pageNumber;
    private Integer nextPage;
    private Integer previousPage;
    private Integer totalPages;


    public UsersWrapper(Page<User> page) {

        this.members = getUsers(page.getContent());
        this.totalPages = page.getTotalPages();
        this.pageNumber = page.getNumber() + 1;
        if(page.hasNext()){
            nextPage = pageNumber +1;
        }
        if(page.hasPrevious()){
            previousPage = pageNumber - 1;
        }
    }

    public List<UserDTO> getMembers() {
        return members;
    }

    public Integer getPageNumber() {
        return pageNumber;
    }

    public Integer getNextPage() {
        return nextPage;
    }

    public Integer getPreviousPage() {
        return previousPage;
    }

    public Integer getTotalPages() {
        return totalPages;
    }

    public List<UserDTO> getUsers(List<User> users) {
        List<UserDTO> userDTOs = new ArrayList<>();
        for (User user : users) {
            userDTOs.add(new UserDTO(user));
        }
        return userDTOs;

    }
}
