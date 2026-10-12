import { Link } from 'react-router'

export default function NotFoundPage() {
  return (
    <section className="py-12 text-center">
      <h1 className="text-xl font-bold">페이지를 찾을 수 없어요.</h1>
      <Link to="/" className="mt-4 inline-block text-sm text-indigo-600 underline">
        현황판으로 가기
      </Link>
    </section>
  )
}
