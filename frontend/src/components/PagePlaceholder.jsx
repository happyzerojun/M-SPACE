/** 아직 만들지 않은 화면. 각 단계에서 실제 화면으로 바꿔요. */
export default function PagePlaceholder({ title, stage }) {
  return (
    <section>
      <h1 className="text-xl font-bold">{title}</h1>
      <p className="mt-2 text-sm text-gray-500">{stage}에서 만들 화면이에요.</p>
    </section>
  )
}
