function(apply_mnn_patch patch_file patch_name)
    execute_process(
        COMMAND git -C "${MNN_ROOT}" apply --reverse --check "${patch_file}"
        RESULT_VARIABLE reverse_result
        OUTPUT_QUIET
        ERROR_QUIET
    )
    if (reverse_result EQUAL 0)
        return()
    endif()

    execute_process(
        COMMAND git -C "${MNN_ROOT}" apply --check "${patch_file}"
        RESULT_VARIABLE check_result
        OUTPUT_VARIABLE check_output
        ERROR_VARIABLE check_error
    )
    if (NOT check_result EQUAL 0)
        message(FATAL_ERROR
            "Cannot apply the MNN ${patch_name} patch: ${check_output}${check_error}"
        )
    endif()

    execute_process(
        COMMAND git -C "${MNN_ROOT}" apply "${patch_file}"
        RESULT_VARIABLE apply_result
        OUTPUT_VARIABLE apply_output
        ERROR_VARIABLE apply_error
    )
    if (NOT apply_result EQUAL 0)
        message(FATAL_ERROR
            "Cannot apply the MNN ${patch_name} patch: ${apply_output}${apply_error}"
        )
    endif()
endfunction()

apply_mnn_patch("${MNN_VULKAN_SAFE_PATCH}" "Vulkan safety")
apply_mnn_patch("${MNN_QWEN3_TTS_PATCH}" "Qwen3-TTS backend")
