<template>
  <div class="title" data-chapterpos="0" ref="titleRef">{{ title }}</div>
  <div
    v-for="(entry, index) in contentEntries"
    :key="index"
    ref="paragraphRef"
    :data-chapterpos="chapterPos[index]"
  >
    <img
      class="full"
      v-if="entry.standaloneImageSource !== undefined"
      :src="resolveImageSource(entry.standaloneImageSource, readWidth)"
      @error.once="proxyImage"
      loading="lazy"
    />
    <p
      v-else
      :style="{ fontFamily, fontSize }"
      v-html="replaceImage(entry.content)"
      @error.capture="handleImgLoadError"
    />
  </div>
</template>

<script setup lang="ts">
import { isLegadoUrl } from '@/utils/utils'
import API from '@api'
import jump from '@/plugins/jump'
import {
  calculateChapterWordCount,
  createImageFallbackResolver,
  getStandaloneImageSource,
  renderSafeChapterHtml,
} from '@/utils/chapterHtml'
import type { webReadConfig } from '@/web'

const store = useBookStore()
const readWidth = computed(() => store.config.readWidth)
const _fontSize = computed(() => store.config.fontSize)
const bookUrl = computed(() => store.readingBook.bookUrl)

const props = defineProps<{
  chapterIndex: number
  contents: Array<string>
  title: string
  spacing: webReadConfig['spacing']
  fontFamily: string
  fontSize: string
}>()

const contentEntries = computed(() => {
  return props.contents.map(content => ({
    content,
    standaloneImageSource: getStandaloneImageSource(content),
  }))
})

const resolveImageSource = (source: string, width: number) => {
  if (isLegadoUrl(source)) {
    return API.getProxyImageUrl(bookUrl.value, source, width)
  }
  return source
}

const replaceImage = (content: string) => {
  return renderSafeChapterHtml(content, source =>
    resolveImageSource(source, _fontSize.value * 2),
  )
}
const resolveImageFallback = createImageFallbackResolver(source =>
  API.getProxyImageUrl(bookUrl.value, source, readWidth.value),
)
const proxyImage = (event: Event) => {
  /* 获取IMG标签原始的src
    <img src="/test" />
    假设location.href = http://example.com
    event.target.src 返回 http://example.com/test
    (event.target as HTMLImageElement)?.getAttribute("src")  返回/test
  */
  const image = event.target as HTMLImageElement | null
  const src = image?.getAttribute('src')
  if (image == null || src == null || src.length === 0) return

  const fallback = resolveImageFallback(image, src)
  if (fallback === undefined) return

  console.log(
    '[ChapterContent]: IMG Load Error, replace src:',
    src,
    '=>',
    fallback,
  )
  image.src = fallback
}

/**
 * 处理传入的IMG标签错误事件，自动替换图片的代理链接
 */
const handleImgLoadError = (event: Event) => {
  if ((event.target as HTMLElement)?.tagName === 'IMG') {
    proxyImage(event)
  }
}

const chapterPos = computed(() => {
  let pos = -1
  return Array.from(props.contents, content => {
    pos += calculateChapterWordCount(content) + 1 //计算上一段的换行符
    return pos
  })
})

const titleRef = ref<HTMLElement>()
const paragraphRef = ref<HTMLParagraphElement[]>()
const scrollToReadedLength = (length: number) => {
  if (length === 0) return
  const paragraphIndex = chapterPos.value.findIndex(
    wordCount => wordCount >= length,
  )
  if (paragraphIndex === -1) return
  nextTick(() => {
    jump(paragraphRef.value![paragraphIndex], {
      duration: 0,
    })
  })
}
defineExpose({
  scrollToReadedLength,
})
let intersectionObserver: IntersectionObserver | null = null
const emit = defineEmits(['readedLengthChange'])
onMounted(() => {
  intersectionObserver = new IntersectionObserver(
    entries => {
      for (const { target, isIntersecting } of entries) {
        if (isIntersecting) {
          emit(
            'readedLengthChange',
            props.chapterIndex,
            parseInt((target as HTMLElement).dataset.chapterpos as string),
          )
        }
      }
    },
    {
      rootMargin: `0px 0px -${window.innerHeight - 24}px 0px`,
    },
  )
  intersectionObserver.observe(titleRef.value!)
  paragraphRef.value!.forEach(element => {
    intersectionObserver!.observe(element)
  })
})

onUnmounted(() => {
  intersectionObserver?.disconnect()
  intersectionObserver = null
})
</script>

<style lang="scss" scoped>
.title {
  margin-bottom: 57px;
  font:
    24px / 32px PingFangSC-Regular,
    HelveticaNeue-Light,
    'Helvetica Neue Light',
    'Microsoft YaHei',
    sans-serif;
}

p {
  display: block;
  word-wrap: break-word;
  /*   word-break: break-all; */
  letter-spacing: calc(v-bind('props.spacing.letter') * 1em);
  line-height: calc(1 + v-bind('props.spacing.line'));
  margin: calc(v-bind('props.spacing.paragraph') * 1em) 0;

  :deep(img) {
    height: 1em;
  }
}

.full {
  display: block;
  width: 100%;
}
</style>
