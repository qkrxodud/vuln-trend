import '@testing-library/jest-dom/vitest'
import { cleanup } from '@testing-library/react'
import { afterEach, beforeEach } from 'vitest'
import { installFakeMedia } from './fake/fake-media.ts'

// 기본은 데스크톱 폭(1280px). 좁은 화면 테스트는 setViewportWidth 로 바꾼다
beforeEach(() => installFakeMedia())

afterEach(() => cleanup())
