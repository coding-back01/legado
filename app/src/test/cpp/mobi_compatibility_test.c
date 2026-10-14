#include <assert.h>
#include <stdint.h>
#include <stdio.h>
#include "mobi.h"
#include "util.h"

int legado_mobi_extract(const char *, const char *, size_t);

int main(int argc, char **argv) {
    /* 小元数据验证大正文上限，不在回归测试中分配数百兆正文。 */
    MOBIData *m = mobi_init();
    assert(m);
    MOBIRecord0Header header = {0};
    m->rh = &header;
    header.text_record_count = 40000;
    header.text_record_size = 8192;
    assert(mobi_get_text_maxsize(m) == 327680000);
    header.text_record_count = UINT16_MAX;
    header.text_record_size = UINT16_MAX;
    assert(mobi_get_text_maxsize(m) == MOBI_NOTSET);
    m->rh = NULL;
    mobi_free(m);
    if (argc != 3) return 0;
    int result = legado_mobi_extract(argv[1], argv[2], 536870912);
    printf("RESULT=%d\n", result);
    return result;
}
