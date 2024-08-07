package io.mosip.resident.dto;

import java.io.Serializable;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * @author Sowmya The Class PacketGeneratorDto.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class PacketGeneratorDto implements Serializable {

	/**
	 * 
	 */
	private static final long serialVersionUID = -1587235057041204701L;

	/** The reason. */
	private String reason;

	/** The registration type. */
	@NotNull(message = "registrationType should not be null ")
	@NotBlank(message = "registrationType should not be empty")
	private String registrationType;

	/** The uin. */
	@NotNull(message = "uin should not be null ")
	@NotBlank(message = "uin should not be empty")
	private String uin;

	/** The center id. */
	@NotNull(message = "centerId should not be null ")
	@NotBlank(message = "centerId should not be empty")
	private String centerId;

	/** The machine id. */
	@NotNull(message = "machineId should not be null ")
	@NotBlank(message = "machineId should not be empty")
	private String machineId;
}
