use jni::{
    objects::{JCharArray, JClass},
    sys::jintArray,
    JNIEnv,
};

// UTF-16 offsets match Kotlin String indices, including surrogate pairs.
fn line_ranges(text: &[u16]) -> Vec<i32> {
    let mut ranges = Vec::new();
    let mut start = 0;
    let mut i = 0;
    while i < text.len() {
        if text[i] == 10 || text[i] == 13 {
            ranges.extend([start as i32, i as i32]);
            if text[i] == 13 && text.get(i + 1) == Some(&10) {
                i += 1;
            }
            start = i + 1;
        }
        i += 1;
    }
    ranges.extend([start as i32, text.len() as i32]);
    ranges
}

#[no_mangle]
pub extern "system" fn Java_takagi_ru_monica_github_reader_NativeCodeIndex_lineRanges(
    env: JNIEnv,
    _class: JClass,
    input: JCharArray,
) -> jintArray {
    let result = (|| {
        let length = env.get_array_length(&input)?;
        let mut text = vec![0u16; length as usize];
        env.get_char_array_region(&input, 0, &mut text)?;
        let ranges = line_ranges(&text);
        let output = env.new_int_array(ranges.len() as i32)?;
        env.set_int_array_region(&output, 0, &ranges)?;
        Ok::<_, jni::errors::Error>(output.into_raw())
    })();
    result.unwrap_or(std::ptr::null_mut())
}

#[cfg(test)]
mod tests {
    use super::*;
    #[test]
    fn mixed_endings_and_unicode_use_utf16_offsets() {
        let input: Vec<u16> = "a\r\n😀\rb\n".encode_utf16().collect();
        assert_eq!(line_ranges(&input), vec![0, 1, 3, 5, 6, 7, 8, 8]);
    }
    #[test]
    fn empty_file_has_one_line() {
        assert_eq!(line_ranges(&[]), vec![0, 0]);
    }

    #[test]
    fn carriage_return_and_surrogate_pair_offsets() {
        let input: Vec<u16> = "a\r😀\rb".encode_utf16().collect();
        assert_eq!(line_ranges(&input), vec![0, 1, 2, 4, 5, 6]);
    }
}
